package com.ciweihaha.oriontv.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import kotlin.math.abs

/**
 * Deterministic gradient poster from a title (same title → same colours).
 * Used wherever a real poster is unavailable; matches the approved mockup's
 * "no-poster → gradient block" rule. Pure drawing, no network.
 */
object PosterArt {

    fun make(context: Context, title: String, widthPx: Int, heightPx: Int): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (top, bottom) = colorsFor(title)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, widthPx * 0.35f, heightPx.toFloat(),
                top, bottom, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)

        // Large first character, centred, low-key.
        val letter = title.firstOrNull()?.toString() ?: "?"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(0x66, 0xFF, 0xFF, 0xFF)
            textSize = widthPx * 0.55f
            textAlign = Paint.Align.CENTER
        }
        val y = heightPx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, widthPx / 2f, y, textPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    /** Two stable colours from the title hash (gold-tinted dark palette). */
    fun colorsFor(title: String): Pair<Int, Int> {
        val hash = abs(title.hashCode())
        val hue = (hash % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.32f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 32f) % 360f, 0.55f, 0.16f))
        return top to bottom
    }
}
