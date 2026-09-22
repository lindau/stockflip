package com.stockflip.testutil

import com.stockflip.GithubReleaseServiceContract
import com.stockflip.UpdateReleaseInfo

class FakeGithubReleaseService(
    private val release: UpdateReleaseInfo? = null
) : GithubReleaseServiceContract {
    override suspend fun getLatestReleaseInfo(): UpdateReleaseInfo? = release
}
