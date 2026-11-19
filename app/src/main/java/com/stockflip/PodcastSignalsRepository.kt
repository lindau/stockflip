package com.stockflip

import java.util.Locale

/**
 * Hämtar poddsignaler från podcast-analysis och cachar dem i minnet (signalerna är färskvaror med
 * 14-dagarsfönster, så de lagras inte i Room). Best-effort: saknad URL eller nätverksfel ger tom data
 * så att UI:t bara döljer sektionen.
 */
class PodcastSignalsRepository(
    private val fetch: suspend () -> List<PodcastSignalDto> = { PodcastAnalysisService().getSignals() },
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
) {
    private var cached: List<PodcastSignalDto>? = null
    private var cachedAt = 0L

    private suspend fun all(): List<PodcastSignalDto> {
        val current = cached
        if (current != null && now() - cachedAt < ttlMillis) return current
        return try {
            fetch().also { cached = it; cachedAt = now() }
        } catch (e: Exception) {
            current.orEmpty()
        }
    }

    suspend fun forTicker(ticker: String): PodcastSignalDto? {
        val key = ticker.uppercase(Locale.US)
        return all().firstOrNull { it.ticker?.uppercase(Locale.US) == key }
    }

    /** Topplista efter score. [market] är `se` eller `us` (tomt = alla). */
    suspend fun top(market: String?, n: Int = 10): List<PodcastSignalDto> =
        all().filter { market == null || it.market == market }.sortedByDescending { it.score }.take(n)

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        val shared by lazy { PodcastSignalsRepository() }
    }
}
