package com.stockflip

import com.stockflip.testutil.FakeGithubReleaseService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    private fun release(versionName: String) = UpdateReleaseInfo(
        versionName = versionName,
        releaseNotes = "notes",
        downloadUrl = "https://example.com/stockflip.apk",
        apkSizeBytes = 100L,
        htmlUrl = null
    )

    @Test
    fun `checkForUpdate returns UpdateAvailable for a newer version`() = runBlocking {
        val checker = AppUpdateChecker(FakeGithubReleaseService(release("1.2.94")))
        val result = checker.checkForUpdate(currentVersionName = "1.2.93")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("1.2.94", (result as UpdateCheckResult.UpdateAvailable).release.versionName)
    }

    @Test
    fun `checkForUpdate returns UpToDate for the same or an older version`() = runBlocking {
        val checker = AppUpdateChecker(FakeGithubReleaseService(release("1.2.93")))
        assertEquals(UpdateCheckResult.UpToDate, checker.checkForUpdate(currentVersionName = "1.2.93"))
    }

    @Test
    fun `checkForUpdate returns CheckFailed when the release service returns null`() = runBlocking {
        val checker = AppUpdateChecker(FakeGithubReleaseService(null))
        assertEquals(UpdateCheckResult.CheckFailed, checker.checkForUpdate(currentVersionName = "1.2.93"))
    }

    @Test
    fun `checkForUpdateRespectingSkip downgrades a skipped version to UpToDate`() = runBlocking {
        val checker = AppUpdateChecker(FakeGithubReleaseService(release("1.2.94")))
        val result = checker.checkForUpdateRespectingSkip(
            currentVersionName = "1.2.93",
            skippedVersion = { "1.2.94" }
        )
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `checkForUpdateRespectingSkip still reports an update not matching the skipped version`() = runBlocking {
        val checker = AppUpdateChecker(FakeGithubReleaseService(release("1.2.94")))
        val result = checker.checkForUpdateRespectingSkip(
            currentVersionName = "1.2.93",
            skippedVersion = { "1.2.90" }
        )
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
    }
}
