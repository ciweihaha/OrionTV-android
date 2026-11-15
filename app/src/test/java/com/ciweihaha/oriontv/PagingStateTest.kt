package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.ui.vod.PagingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingStateTest {

    @Test
    fun `fresh state allows page 1 and reports nextPage 1`() {
        val paging = PagingState()
        assertEquals(0, paging.currentPage)
        assertTrue(paging.hasMore)
        assertFalse(paging.isLoading)
        assertEquals(1, paging.nextPage)
        assertTrue(paging.canLoadMore())
    }

    @Test
    fun `loading blocks overlapping loads`() {
        val paging = PagingState()
        paging.onLoadStarted()
        assertTrue(paging.isLoading)
        assertFalse(paging.canLoadMore())
        paging.onPageLoaded(1, true)
        assertFalse(paging.isLoading)
        assertEquals(1, paging.currentPage)
        assertTrue(paging.hasMore)
        assertEquals(2, paging.nextPage)
        assertTrue(paging.canLoadMore())
    }

    @Test
    fun `last page stops further loads`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onPageLoaded(3, false)
        assertEquals(3, paging.currentPage)
        assertFalse(paging.hasMore)
        assertFalse(paging.canLoadMore())
    }

    @Test
    fun `failed load keeps page and hasMore for retry`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onPageLoaded(1, true)
        paging.onLoadStarted()
        paging.onLoadFailed()
        assertFalse(paging.isLoading)
        assertEquals(1, paging.currentPage)
        assertTrue(paging.hasMore)
        assertTrue(paging.canLoadMore())
        assertEquals(2, paging.nextPage)
    }

    @Test
    fun `reset returns to page 1 state`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onPageLoaded(2, false)
        paging.reset()
        assertEquals(0, paging.currentPage)
        assertTrue(paging.hasMore)
        assertFalse(paging.isLoading)
        assertEquals(1, paging.nextPage)
    }

    @Test
    fun `shouldLoadMore only near the end`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onPageLoaded(1, true)
        // 60 items loaded; last visible at 50 is within threshold 6 of 60.
        assertTrue(paging.shouldLoadMore(lastVisiblePosition = 54, totalItemCount = 60))
        assertTrue(paging.shouldLoadMore(lastVisiblePosition = 59, totalItemCount = 60))
        // Last visible at 40 is still far from the end.
        assertFalse(paging.shouldLoadMore(lastVisiblePosition = 40, totalItemCount = 60))
        // Empty list never triggers.
        assertFalse(paging.shouldLoadMore(lastVisiblePosition = 0, totalItemCount = 0))
    }

    @Test
    fun `first empty page keeps hasMore second consecutive empty page ends it`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onEmptyPage(1)
        assertEquals(1, paging.currentPage)
        assertTrue(paging.hasMore)
        assertTrue(paging.canLoadMore())
        assertEquals(2, paging.nextPage)
        paging.onLoadStarted()
        paging.onEmptyPage(2)
        assertEquals(2, paging.currentPage)
        assertFalse(paging.hasMore)
        assertFalse(paging.canLoadMore())
    }

    @Test
    fun `non-empty page resets the empty streak`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onEmptyPage(1)
        paging.onLoadStarted()
        paging.onPageLoaded(2, true)
        assertEquals(0, paging.consecutiveEmptyPages)
        paging.onLoadStarted()
        paging.onEmptyPage(3)
        assertTrue(paging.hasMore)
    }

    @Test
    fun `reset clears the empty streak`() {
        val paging = PagingState()
        paging.onLoadStarted()
        paging.onEmptyPage(1)
        paging.reset()
        assertEquals(0, paging.consecutiveEmptyPages)
        assertTrue(paging.hasMore)
    }

    @Test
    fun `shouldLoadMore is false while loading or at the last page`() {
        val paging = PagingState()
        paging.onLoadStarted()
        assertFalse(paging.shouldLoadMore(lastVisiblePosition = 59, totalItemCount = 60))
        paging.onPageLoaded(1, false)
        assertFalse(paging.shouldLoadMore(lastVisiblePosition = 59, totalItemCount = 60))
    }
}
