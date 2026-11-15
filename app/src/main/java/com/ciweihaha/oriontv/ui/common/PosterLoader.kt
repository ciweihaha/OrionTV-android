package com.ciweihaha.oriontv.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Real poster images (VodItem.posterUrl, parsed long ago but never
 * shown). No image library is on the classpath, so this is a small
 * hand-rolled loader: in-memory LRU cache, 10s timeout, 2MB body cap,
 * downsampled to the requested size. Failures keep the placeholder the
 * caller already set; a recycled view never receives another item's
 * poster (the URL tag is checked on completion).
 *
 * [scope] must be the owning screen's lifecycle scope so loads stop
 * with the screen instead of retrying after destroy.
 */
object PosterLoader {

    /** ~8MB of decoded posters. */
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    /** Posters bigger than this are rejected, not decoded. */
    const val MAX_POSTER_BYTES = 2 * 1024 * 1024

    const val TIMEOUT_MS = 10_000

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * D6: downloads in flight, keyed by URL + requested size. Without
     * this, every ImageView that asked for the same poster started its
     * own HTTP download — fast scroll (rebind), a category switch, or
     * the same poster in several rows fired N identical requests for
     * one image. Waiters share the single download and still get the
     * recycled-view tag check on delivery.
     */
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

    fun load(
        imageView: ImageView,
        url: String?,
        widthPx: Int,
        heightPx: Int,
        scope: CoroutineScope,
    ) {
        val clean = url?.takeIf { it.isNotBlank() } ?: return
        imageView.tag = clean
        cache.get(clean)?.let {
            imageView.setImageBitmap(it)
            return
        }
        val key = "$clean|${widthPx}x$heightPx"
        val deferred = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                scope.async(Dispatchers.IO) {
                    try {
                        downloadBitmap(clean, widthPx, heightPx)
                            ?.also { cache.put(clean, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }.also { d ->
                    d.invokeOnCompletion {
                        synchronized(inFlight) {
                            if (inFlight[key] === d) inFlight.remove(key)
                        }
                    }
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            val bitmap = try {
                deferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bitmap != null && imageView.tag == clean) imageView.setImageBitmap(bitmap)
        }
    }

    private fun downloadBitmap(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "OrionTV-android/0.1.0")
        try {
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared > MAX_POSTER_BYTES) return null
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.readBytesCapped(MAX_POSTER_BYTES) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }

    /** Largest power-of-two downsample that stays at/above the target. */
    internal fun sampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= reqWidth && srcHeight / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    private fun java.io.InputStream.readBytesCapped(maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
