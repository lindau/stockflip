package com.stockflip

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
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
    // GitHub:s API kräver en User-Agent-header och svarar annars 403 Forbidden.
    @Headers("User-Agent: StockFlip-Android")
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
    // TEMP DIAGNOSTIC — ta bort denna metod tillsammans med alla "TEMP DIAGNOSTIC"-markeringar.
    suspend fun getLatestReleaseInfoDiagnostic(): String? = null
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

    // TEMP DIAGNOSTIC — ta bort tillsammans med alla "TEMP DIAGNOSTIC"-markeringar.
    @Volatile private var lastFailureDiagnostic: String? = null

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
            val info = mapper.getLatestReleaseInfo()
            lastFailureDiagnostic = if (info == null) { // TEMP DIAGNOSTIC
                "Mapper returnerade null (release-JSON saknar tag_name, stockflip.apk, browser_download_url eller size)"
            } else {
                null
            }
            info
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest release: ${e.message}")
            lastFailureDiagnostic = "${e::class.simpleName}: ${e.message}" // TEMP DIAGNOSTIC
            null
        }
    }

    // TEMP DIAGNOSTIC
    override suspend fun getLatestReleaseInfoDiagnostic(): String? = lastFailureDiagnostic
}
