package com.stockflip

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("Live network smoke tests; run manually when needed.")
class GithubReleaseLiveTest {

    @Test
    fun `getLatestReleaseInfo returns a plausible version from the real GitHub API`() = runBlocking {
        val release = GithubReleaseService.getLatestReleaseInfo()
        assertNotNull(release)
        assertTrue(release!!.versionName.matches(Regex("""\d+\.\d+\.\d+""")))
    }
}
