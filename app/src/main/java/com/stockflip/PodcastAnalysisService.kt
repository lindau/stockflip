package com.stockflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * DTOs matching podcast-analysis's /api/companies response shape
 * (podcast-pipeline repo, web/insights.py / dashboard_server.py's
 * _build_companies_payload -- see that project for the source of truth).
 */
data class PodcastCompaniesResponse(
    val companies: List<PodcastCompanyDto> = emptyList()
)

data class PodcastCompanyDto(
    val ticker: String? = null,
    val name: String? = null,
    val mentions: List<PodcastMentionDto> = emptyList()
)

data class PodcastMentionDto(
    val observation_id: String? = null,
    val podcast: String? = null,
    val title: String? = null,
    val published: String? = null,
    val recommendation: String? = null,
    val stance: String? = null,
    val stance_raw: String? = null,
    val thesis: List<String> = emptyList(),
    val risks: List<String> = emptyList()
)

interface PodcastAnalysisApi {
    @GET("api/companies")
    suspend fun getCompanies(): PodcastCompaniesResponse
}

/**
 * Client for the personal podcast-analysis homelab service (see
 * BuildConfig.PODCAST_ANALYSIS_BASE_URL -- deliberately unset by default,
 * see app/build.gradle's comment: this must never be baked into a shared
 * APK, only set in the developer's own local.properties). Reached over
 * Tailscale (tailscale serve), never the public internet.
 */
class PodcastAnalysisService(
    private val baseUrl: String = BuildConfig.PODCAST_ANALYSIS_BASE_URL
) {
    private val api: PodcastAnalysisApi? by lazy {
        if (baseUrl.isBlank()) {
            null
        } else {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PodcastAnalysisApi::class.java)
        }
    }

    /** True when a base URL is configured -- callers should skip entirely otherwise. */
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /**
     * Fetches every company's mentions and returns them keyed by ticker
     * (uppercased, matching how StockFlip stores tickers elsewhere).
     *
     * Deliberately lets a network/parsing failure propagate (unlike most
     * of this app's other best-effort fetches) -- the caller
     * (PodcastObservationWorker) records the failure via
     * PodcastObservationSettings so it's visible in the app's own UI. This
     * is a personal integration with no server dashboard and no adb
     * access to fall back on for diagnosing a silent "found nothing"; a
     * swallowed exception here would be indistinguishable from "synced
     * fine, no matches" -- exactly the ambiguity that made an earlier
     * "no observations showing" report hard to diagnose.
     */
    suspend fun getObservationsByTicker(): Map<String, List<PodcastMentionDto>> = withContext(Dispatchers.IO) {
        val service = api ?: return@withContext emptyMap()
        val response = service.getCompanies()
        response.companies
            .filter { !it.ticker.isNullOrBlank() }
            .associate { it.ticker!!.uppercase(Locale.US) to it.mentions }
    }
}

/**
 * podcast-analysis stores `published` as an RFC-1123-ish string (e.g.
 * "Thu, 18 Jun 2026 22:01:00 GMT"). Parsed with SimpleDateFormat rather
 * than java.time, matching SecInsiderTransactionService's date handling
 * elsewhere in this codebase (minSdk 24, no java.time desugaring
 * configured).
 */
internal fun parsePublishedAtMillis(published: String?): Long? {
    if (published.isNullOrBlank()) return null
    val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
    return runCatching { parser.parse(published)?.time }.getOrNull()
}

internal fun PodcastMentionDto.toEntity(ticker: String, companyName: String?): PodcastObservationEntity? {
    val id = observation_id ?: return null
    return PodcastObservationEntity(
        observationId = id,
        ticker = ticker,
        companyName = companyName,
        podcast = podcast.orEmpty(),
        episodeTitle = title,
        recommendation = recommendation,
        stance = stance,
        exactQuote = null,
        thesis = thesis,
        risks = risks,
        publishedAtMillis = parsePublishedAtMillis(published)
    )
}
