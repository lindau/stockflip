package com.stockflip.repository

import android.util.Log
import com.stockflip.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface PodcastMentionApi {
    @GET("api/mentioned-tickers")
    suspend fun getMentionedTickers(): MentionedTickersResponse
}

data class MentionedTickersResponse(val tickers: List<String> = emptyList())

/**
 * Hämtar tickers omnämnda i poddavsnitt från podcast-pipeline, en privat instans
 * som bara är nåbar via Tailscale. Delar BuildConfig.PODCAST_ANALYSIS_BASE_URL med
 * PodcastAnalysisService (samma homelab-tjänst, bara ett annat -- lättare -- endpoint:
 * /api/mentioned-tickers istället för /api/companies). Best effort: om instansen inte
 * svarar (Tailscale avstängt, instansen nere) misslyckas anropet tyst och senaste kända
 * resultat (eller en tom mängd) används — mikrofon-badgen visas då bara inte.
 */
class PodcastMentionRepository(
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val cacheTTL: Long = TimeUnit.MINUTES.toMillis(15),
    private val api: PodcastMentionApi? = createApi(),
) {
    private val tag = "PodcastMentionRepo"
    private var cache: Set<String> = emptySet()
    private var cacheTimestamp: Long = 0L

    suspend fun getMentionedTickers(): Set<String> = withContext(Dispatchers.IO) {
        if (api == null) return@withContext emptySet()

        val now = timeProvider()
        if (cache.isNotEmpty() && now - cacheTimestamp < cacheTTL) {
            return@withContext cache
        }

        try {
            val response = api.getMentionedTickers()
            val normalized = response.tickers.map(::normalizeTicker).toSet()
            cache = normalized
            cacheTimestamp = now
            normalized
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch podcast mentions: ${e.message}")
            cache
        }
    }

    companion object {
        /** Bästa-försök-normalisering för att matcha ihop Yahoo-tickers (t.ex.
         * "VOLV-B.ST") med tickers som podcast-pipeline lagrar friare (t.ex. "VOLV B"). */
        fun normalizeTicker(raw: String): String =
            raw.substringBefore(".").uppercase().replace("-", "").replace(" ", "")

        private fun createApi(): PodcastMentionApi? {
            val baseUrl = BuildConfig.PODCAST_ANALYSIS_BASE_URL
            if (baseUrl.isBlank()) return null
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(PodcastMentionApi::class.java)
        }
    }
}
