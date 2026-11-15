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
    fun `parses bare version and detects update`() = runTest {
        val checker = UpdateChecker(fetcher = { "1.7.2" }, currentVersion = "0.1.0")
        val result = checker.check().getOrThrow()
        assertEquals("1.7.2", result.latestVersion)
        assertTrue(result.updateAvailable)
    }

    @Test
    fun `parses json version field`() = runTest {
        val checker = UpdateChecker(
            fetcher = { """{"version":"1.7.3"}""" },
            currentVersion = "1.7.2",
        )
        val result = checker.check().getOrThrow()
        assertEquals("1.7.3", result.latestVersion)
        assertTrue(result.updateAvailable)
    }

    @Test
    fun `no update when versions are equal`() = runTest {
        val checker = UpdateChecker(fetcher = { "0.1.0" }, currentVersion = "0.1.0")
        val result = checker.check().getOrThrow()
        assertFalse(result.updateAvailable)
    }

    @Test
    fun `falls back to second endpoint when primary fails`() = runTest {
        val checker = UpdateChecker(
            fetcher = { url ->
                if (url.contains("oriontv-version.json")) """{"version":"1.7.3"}"""
                else throw IllegalStateException("primary down")
            },
            currentVersion = "1.7.2",
        )
        val result = checker.check().getOrThrow()
        assertEquals("1.7.3", result.latestVersion)
    }

    @Test
    fun `invalid body fails the check`() = runTest {
        val checker = UpdateChecker(fetcher = { "not-a-version" }, currentVersion = "0.1.0")
        assertTrue(checker.check().isFailure)
    }

    @Test
    fun `cancellation is not swallowed into a failure result`() = runTest {
        val checker = UpdateChecker(
            fetcher = { throw kotlinx.coroutines.CancellationException("gone") },
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
    fun `parseVersion handles both shapes`() {
        assertEquals("1.7.2", UpdateChecker.parseVersion("1.7.2"))
        assertEquals("1.7.2", UpdateChecker.parseVersion("v1.7.2"))
        assertEquals("1.7.2", UpdateChecker.parseVersion("""{"version": "1.7.2"}"""))
        assertNull(UpdateChecker.parseVersion("garbage"))
    }

    @Test
    fun `compareVersions is numeric`() {
        assertTrue(UpdateChecker.compareVersions("1.7.2", "1.7.10") < 0)
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.7.2") > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.7.2", "1.7.2"))
    }
}
