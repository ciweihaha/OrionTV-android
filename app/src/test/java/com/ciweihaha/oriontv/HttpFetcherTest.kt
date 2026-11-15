package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.HttpFetcher
import com.ciweihaha.oriontv.data.source.readBytesCapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R15-G: the response body cap. A body over [HttpFetcher.MAX_BODY_BYTES]
 * is rejected instead of being read unbounded into memory.
 */
class HttpFetcherTest {

    @Test
    fun `body under the cap reads fully`() {
        val body = "hello".toByteArray(Charsets.UTF_8)
        val read = body.inputStream().readBytesCapped(HttpFetcher.MAX_BODY_BYTES)
        assertEquals("hello", read!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `body over the cap is rejected`() {
        val body = ByteArray(HttpFetcher.MAX_BODY_BYTES + 1) { 'x'.code.toByte() }
        val read = body.inputStream().readBytesCapped(HttpFetcher.MAX_BODY_BYTES)
        assertNull(read)
    }

    @Test
    fun `body exactly at the cap reads fully`() {
        val body = ByteArray(HttpFetcher.MAX_BODY_BYTES) { 'x'.code.toByte() }
        val read = body.inputStream().readBytesCapped(HttpFetcher.MAX_BODY_BYTES)
        assertEquals(HttpFetcher.MAX_BODY_BYTES, read!!.size)
    }
}
