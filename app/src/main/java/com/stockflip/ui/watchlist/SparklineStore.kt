package com.stockflip.ui.watchlist

import com.stockflip.ChartPeriod
import com.stockflip.MarketDataService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Sparklinedata per symbol: en månads kurser nedsamplade till några få punkter, cachade en stund. */
internal object SparklineStore {
    private const val TTL_MS = 15 * 60 * 1000L
    private const val MAX_POINTS = 24
    private class Entry(val values: List<Double>, val at: Long)
    private val cache = ConcurrentHashMap<String, Entry>()

    /** Jämnt urval av högst [max] punkter, alltid med första och sista värdet. */
    fun downsample(values: List<Double>, max: Int = MAX_POINTS): List<Double> {
        if (values.size <= max || max < 2) return values
        return List(max) { i -> values[(i.toLong() * (values.size - 1) / (max - 1)).toInt()] }
    }

    /** Ersätter sista punkten med aktuell kurs så att kurvans slut stämmer med raden; oförändrad om kurs saknas. */
    fun withLivePrice(series: List<Double>?, price: Double): List<Double>? =
        if (series == null || series.size < 2 || price <= 0.0) series else series.dropLast(1) + price

    fun cached(symbol: String, now: Long = System.currentTimeMillis()): List<Double>? =
        cache[symbol]?.takeIf { now - it.at < TTL_MS }?.values

    /**
     * Hämtar saknade/utgångna serier (högst tre samtidigt) och returnerar alla kända serier för [symbols].
     * Misslyckade hämtningar hoppas över (raden visas då utan sparkline).
     */
    suspend fun load(symbols: Collection<String>, service: MarketDataService, now: Long = System.currentTimeMillis()): Map<String, List<Double>> {
        val gate = Semaphore(3)
        coroutineScope {
            symbols.distinct().filter { cached(it, now) == null }.map { symbol ->
                async {
                    gate.withPermit {
                        val prices = try { service.getIntradayChart(symbol, ChartPeriod.MONTH)?.prices } catch (e: Exception) { null }
                        if (!prices.isNullOrEmpty() && prices.size >= 2) cache[symbol] = Entry(downsample(prices), now)
                    }
                }
            }.awaitAll()
        }
        return symbols.mapNotNull { s -> cached(s, now)?.let { s to it } }.toMap()
    }
}
