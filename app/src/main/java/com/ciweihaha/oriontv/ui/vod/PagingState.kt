package com.ciweihaha.oriontv.ui.vod

/**
 * Paged-list state machine for the VOD browse screen (round 9).
 *
 * Pure logic, no Android dependency, so it is unit-testable. The screen
 * owns one instance per category view and drives it like this:
 *
 * - [reset] when the category changes (page 1 is loaded fresh).
 * - [canLoadMore] gates every load: no overlapping loads, no loads past
 *   the last page.
 * - [onLoadStarted] when a load begins; [onPageLoaded] on success
 *   (advances the page, adopts the new `hasMore`); [onLoadFailed] on a
 *   failed load (keeps the page and `hasMore`, so scrolling can retry).
 * - [shouldLoadMore] answers the scroll listener: true only when a load
 *   is allowed and the focused/visible position is near the end.
 */
class PagingState {

    /** Last page loaded successfully (0 = nothing loaded yet). */
    var currentPage: Int = 0
        private set

    /** True when the last successful page said there is another page. */
    var hasMore: Boolean = true
        private set

    /** True while a page load is in flight. */
    var isLoading: Boolean = false
        private set

    /** Consecutive pages that came back empty (M10/M11). */
    var consecutiveEmptyPages: Int = 0
        private set

    /** Next page to request (1-based). */
    val nextPage: Int get() = currentPage + 1

    fun reset() {
        currentPage = 0
        hasMore = true
        isLoading = false
        consecutiveEmptyPages = 0
    }

    fun canLoadMore(): Boolean = !isLoading && hasMore

    fun onLoadStarted() {
        isLoading = true
    }

    fun onPageLoaded(page: Int, pageHasMore: Boolean) {
        isLoading = false
        currentPage = page
        hasMore = pageHasMore
        consecutiveEmptyPages = 0
    }

    /**
     * M10: an empty page is not automatically "the end", but it is not
     * infinitely retryable either. The first consecutive empty page
     * keeps `hasMore` so the next page can be tried; the second one in
     * a row ends pagination ([hasMore] = false) instead of retrying
     * the same empty page forever. The page cursor advances past the
     * empty page so the next load requests the following page.
     */
    fun onEmptyPage(page: Int) {
        isLoading = false
        currentPage = page
        consecutiveEmptyPages++
        if (consecutiveEmptyPages >= MAX_CONSECUTIVE_EMPTY_PAGES) {
            hasMore = false
        }
    }

    fun onLoadFailed() {
        isLoading = false
    }

    /**
     * True when the user has scrolled near the end of the loaded items
     * and another page may be fetched. [lastVisiblePosition] is the
     * adapter position of the last visible item; [totalItemCount] is the
     * adapter item count. Nothing triggers when the list is empty.
     */
    fun shouldLoadMore(
        lastVisiblePosition: Int,
        totalItemCount: Int,
        threshold: Int = LOAD_MORE_THRESHOLD,
    ): Boolean =
        canLoadMore() &&
            totalItemCount > 0 &&
            lastVisiblePosition >= totalItemCount - threshold

    companion object {
        /** Start loading the next page this many items before the end. */
        const val LOAD_MORE_THRESHOLD = 6
        /** Empty pages in a row after which pagination stops (M10). */
        const val MAX_CONSECUTIVE_EMPTY_PAGES = 2
    }
}
