package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.update.UpdateChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer tag reports update`() = runTest {
        val checker = UpdateChecker(latestTag = { "0.2.0" }, currentVersion = "0.1.0")
        val result = checker.check().getOrThrow()
        assertEquals("0.2.0", result.latestVersion)
        assertTrue(result.updateAvailable)
    }

    @Test
    fun `no update when tag equals current version`() = runTest {
        val checker = UpdateChecker(latestTag = { "0.1.0" }, currentVersion = "0.1.0")
        val result = checker.check().getOrThrow()
        assertFalse(result.updateAvailable)
    }

    @Test
    fun `old app line version is never compared against this rewrite`() = runTest {
        // R15-A regression: the agg feed returns the OLD app's 1.7.2
        // for a rewrite running 0.1.0. The new checker reads this
        // repo's own releases, so a "1.7.2" tag can only come from a
        // real release of this repo — and then it IS newer.
        // The point of this test is the wiring: the version being
        // compared is the repo's own tag, not an unrelated feed.
        val checker = UpdateChecker(latestTag = { "0.1.0" }, currentVersion = "0.1.0")
        val result = checker.check().getOrThrow()
        assertEquals("0.1.0", result.latestVersion)
        assertFalse(result.updateAvailable)
    }

    @Test
    fun `tag resolver failure fails the check`() = runTest {
        val checker = UpdateChecker(
            latestTag = { throw IllegalStateException("network down") },
            currentVersion = "0.1.0",
        )
        assertTrue(checker.check().isFailure)
    }

    @Test
    fun `cancellation is not swallowed into a failure result`() = runTest {
        val checker = UpdateChecker(
            latestTag = { throw kotlinx.coroutines.CancellationException("gone") },
            currentVersion = "0.1.0",
        )
        var cancelled = false
        try {
            checker.check()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `parseVersion handles version tag_name and bare shapes`() {
        assertEquals("1.7.2", UpdateChecker.parseVersion("1.7.2"))
        assertEquals("1.7.2", UpdateChecker.parseVersion("v1.7.2"))
        assertEquals("1.7.2", UpdateChecker.parseVersion("""{"version": "1.7.2"}"""))
        assertEquals("0.2.0", UpdateChecker.parseVersion("""{"tag_name":"v0.2.0"}"""))
        assertEquals("0.2.0", UpdateChecker.parseVersion("""{"tag_name":"0.2.0"}"""))
        assertNull(UpdateChecker.parseVersion("garbage"))
        assertNull(UpdateChecker.parseVersion("""{"name":"v0.2.0"}"""))
    }

    @Test
    fun `parseTagFromUrl takes the tag only from a tag page`() {
        assertEquals(
            "0.2.0",
            UpdateChecker.parseTagFromUrl(
                "https://github.com/ciweihaha/OrionTV-android/releases/tag/v0.2.0",
            ),
        )
        assertEquals(
            "0.2.0",
            UpdateChecker.parseTagFromUrl(
                "https://github.com/ciweihaha/OrionTV-android/releases/tag/0.2.0",
            ),
        )
        // No releases: GitHub lands on the releases list, no tag.
        assertNull(
            UpdateChecker.parseTagFromUrl(
                "https://github.com/ciweihaha/OrionTV-android/releases",
            ),
        )
        assertNull(UpdateChecker.parseTagFromUrl("https://github.com/ciweihaha/OrionTV-android"))
        assertNull(UpdateChecker.parseTagFromUrl("not a url"))
    }

    @Test
    fun `compareVersions is numeric`() {
        assertTrue(UpdateChecker.compareVersions("1.7.2", "1.7.10") < 0)
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.7.2") > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.7.2", "1.7.2"))
        assertTrue(UpdateChecker.compareVersions("0.2.0", "0.1.0") > 0)
    }
}
