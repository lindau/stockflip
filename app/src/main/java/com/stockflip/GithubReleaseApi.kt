package com.stockflip

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

data class GithubReleaseDto(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val assets: List<GithubReleaseAssetDto> = emptyList()
)

data class GithubReleaseAssetDto(
    val name: String? = null,
    val browser_download_url: String? = null,
    val size: Long? = null
)

interface GithubReleaseApi {
    @GET("repos/lindau/StockFlip/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseDto
}

data class UpdateReleaseInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String?
)

interface GithubReleaseServiceContract {
    suspend fun getLatestReleaseInfo(): UpdateReleaseInfo?
}

/**
 * Mappar GitHub:s releases/latest-svar till [UpdateReleaseInfo]. Egen klass (inte bara
 * en funktion på [GithubReleaseService]) så den kan testas mot en MockWebServer-baserad
 * [GithubReleaseApi] utan att gå via singletonens hårdkodade https://api.github.com/ --
 * samma uppdelning som YahooFinanceService/YahooMarketDataServiceImpl.
 */
class GithubReleaseMapper(private val api: GithubReleaseApi) {
    suspend fun getLatestReleaseInfo(): UpdateReleaseInfo? {
        val release = api.getLatestRelease()
        val tagName = release.tag_name ?: return null
        val asset = release.assets.firstOrNull { it.name == APK_ASSET_NAME } ?: return null
        val downloadUrl = asset.browser_download_url ?: return null
        val size = asset.size ?: return null
        return UpdateReleaseInfo(
            versionName = stripVersionPrefix(tagName),
            releaseNotes = release.body.orEmpty(),
            downloadUrl = downloadUrl,
            apkSizeBytes = size,
            htmlUrl = release.html_url
        )
    }

    companion object {
        private const val APK_ASSET_NAME = "stockflip.apk"
    }
}

/**
 * Klient mot det publika GitHub-repot lindau/StockFlip:s releases-API, över vanlig HTTPS --
 * kräver ingen autentisering (publikt repo). Detta är källan StockFlip använder för att
 * hålla koll på om det finns en nyare release att hämta hem, se AppUpdateChecker.
 */
object GithubReleaseService : GithubReleaseServiceContract {
    private const val TAG = "GithubReleaseService"
    private const val BASE_URL = "https://api.github.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val mapper = GithubReleaseMapper(retrofit.create(GithubReleaseApi::class.java))

    override suspend fun getLatestReleaseInfo(): UpdateReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            mapper.getLatestReleaseInfo()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest release: ${e.message}")
            null
        }
    }
}
