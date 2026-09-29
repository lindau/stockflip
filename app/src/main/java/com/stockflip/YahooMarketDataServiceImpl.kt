package com.stockflip

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Yahoo Finance-backed implementation for chart-based market data.
 *
 * This class is intentionally constructed with a [YahooFinanceApi] so tests can inject
 * a Retrofit client configured with MockWebServer.
 */
class YahooMarketDataServiceImpl(
    private val api: YahooFinanceApi,
    private val clock: () -> Long = System::currentTimeMillis
) {
    // De flesta metoderna nedan svarar på frågor som alla besvaras av samma
    // v8/finance/chart/{symbol}-anrop (pris, valuta, börs, föregående stängning,
    // 52-veckors high/low, företagsnamn). [quoteMeta] gör ETT nätverksanrop per
    // symbol och en kort TTL-cache gör att flera accessorer för samma symbol inom
    // cache-fönstret återanvänder samma svar i stället för att var och en gör sitt
    // eget anrop.
    // Single-flight: samtidiga frågor för samma symbol (t.ex. flera bevakningar på samma
    // aktie vid listuppdatering, eller aktiedata + bevakningar när aktiedetaljen öppnas)
    // delar på ett pågående anrop i stället för att göra varsitt.
    private val quoteMetaCache = SingleFlightCache<String, Meta>(clock)

    private suspend fun quoteMeta(symbol: String): Meta? =
        quoteMetaCache.getOrLoad(symbol, QUOTE_META_TTL_MS) { fetchMeta(symbol) }

    // Grafdata per symbol och period — byte tillbaka till en period eller återbesök på en aktie
    // visar grafen direkt i stället för att hämta om den.
    private val chartCache = SingleFlightCache<Pair<String, ChartPeriod>, IntradayChartData>(clock)

    // SMA-värden baseras på dagsstängningar och ändras därför inte mellan varje
    // 1-minutersuppdatering under handelstid — en längre TTL undviker onödiga anrop.
    private val smaCache = SingleFlightCache<Pair<String, Int>, Double>(clock)

    // Historisk SMA-serie för grafen — nyckeln inkluderar vald graf-period eftersom hur långt
    // tillbaka i tiden serien behöver sträcka sig beror på det.
    private val smaSeriesCache = SingleFlightCache<Triple<String, Int, ChartPeriod>, List<SmaPoint>>(clock)

    // RSI och Bollinger delar dagsstängningar med SMA. Råa closes cachas per symbol och
    // range-bucket så att flera indikatorer för samma aktie ger ETT nätverksanrop.
    private val dailyClosesCache = SingleFlightCache<Pair<String, String>, List<Pair<Long, Double>>>(clock)
    private val rsiSeriesCache = SingleFlightCache<Triple<String, Int, ChartPeriod>, List<RsiPoint>>(clock)
    private val bollingerSeriesCache =
        SingleFlightCache<Pair<Triple<String, Int, ChartPeriod>, Double>, List<BollingerPoint>>(clock)

    private suspend fun fetchMeta(symbol: String): Meta? {
        return try {
            Log.d(TAG, "Fetching quote data")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "Yahoo API error while fetching quote data: ${response.chart.error.description}")
                return null
            }
            val result: Result? = response.chart?.result?.firstOrNull()
            if (result == null) {
                Log.e(TAG, "No result found while fetching quote data")
            }
            result?.meta
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch quote data: ${e.message}", e)
            null
        }
    }

    suspend fun getStockPrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        val price = quoteMeta(symbol)?.regularMarketPrice
        if (price == null || price.isNaN() || price <= 0.0) {
            Log.e(TAG, "No valid price found in stock price response")
            return@withContext null
        }
        price
    }

    suspend fun getCompanyName(symbol: String): String? = withContext(Dispatchers.IO) {
        quoteMeta(symbol)?.let { meta: Meta ->
            meta.longName ?: meta.shortName ?: meta.symbol
        }
    }

    suspend fun getCurrency(symbol: String): String? = withContext(Dispatchers.IO) {
        // Faller tillbaka på en gissning från tickersuffixet både när valutafältet
        // saknas i ett lyckat svar OCH när själva hämtningen misslyckas helt — en
        // bredare gissning är bättre än null när Yahoo är nere.
        val currency: String? = quoteMeta(symbol)?.currency
        if (!currency.isNullOrBlank()) {
            return@withContext currency
        }
        CurrencyHelper.getCurrencyFromSymbol(symbol)
    }

    suspend fun getExchange(symbol: String): String? = withContext(Dispatchers.IO) {
        val exchange: String? = quoteMeta(symbol)?.exchangeName
        if (!exchange.isNullOrBlank()) exchange else null
    }

    suspend fun getPreviousClose(symbol: String): Double? = withContext(Dispatchers.IO) {
        val meta = quoteMeta(symbol)
        val previousClose: Double? = meta?.regularMarketPreviousClose ?: meta?.chartPreviousClose
        if (previousClose == null || previousClose.isNaN() || previousClose <= 0.0) {
            Log.w(TAG, "No valid previous close found")
            return@withContext null
        }
        previousClose
    }

    suspend fun getDailyChangePercent(symbol: String): Double? = withContext(Dispatchers.IO) {
        val meta = quoteMeta(symbol) ?: return@withContext null
        val directChangePercent = meta.regularMarketChangePercent?.takeIf { !it.isNaN() }
        if (directChangePercent != null) {
            return@withContext directChangePercent
        }
        val currentPrice: Double? = meta.regularMarketPrice
        val previousClose: Double? = meta.regularMarketPreviousClose ?: meta.chartPreviousClose
        if (
            currentPrice == null ||
            currentPrice.isNaN() ||
            previousClose == null ||
            previousClose.isNaN() ||
            previousClose <= 0.0
        ) {
            Log.w(TAG, "Cannot compute daily change from response data")
            return@withContext null
        }
        ((currentPrice - previousClose) / previousClose) * 100.0
    }

    suspend fun getATH(symbol: String): Double? = withContext(Dispatchers.IO) {
        val meta = quoteMeta(symbol)
        val high: Double? = meta?.fiftyTwoWeekHigh
        if (high != null && !high.isNaN() && high > 0.0) {
            return@withContext high
        }
        val dayHigh: Double? = meta?.regularMarketDayHigh
        if (dayHigh != null && !dayHigh.isNaN() && dayHigh > 0.0) {
            return@withContext dayHigh
        }
        null
    }

    suspend fun getAllTimeHigh(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching all-time high")
            val response: YahooFinanceResponse = api.getIntradayChart(symbol, range = "max", interval = "1mo")
            if (response.chart?.error != null) {
                Log.e(TAG, "Yahoo API error while fetching all-time high: ${response.chart.error.description}")
                return@withContext getATH(symbol)
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: return@withContext getATH(symbol)
            val meta = result.meta
            val quote = result.indicators?.quote?.firstOrNull()
            val historicalHigh = (quote?.high.orEmpty() + quote?.close.orEmpty())
                .mapNotNull { value -> value?.takeIf { !it.isNaN() && it > 0.0 } }
                .maxOrNull()

            val candidates = listOfNotNull(
                historicalHigh,
                meta?.regularMarketPrice?.takeIf { !it.isNaN() && it > 0.0 },
                meta?.fiftyTwoWeekHigh?.takeIf { !it.isNaN() && it > 0.0 },
                meta?.regularMarketDayHigh?.takeIf { !it.isNaN() && it > 0.0 }
            )

            candidates.maxOrNull() ?: getATH(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all-time high: ${e.message}", e)
            getATH(symbol)
        }
    }

    suspend fun get52WeekLow(symbol: String): Double? = withContext(Dispatchers.IO) {
        val low: Double? = quoteMeta(symbol)?.fiftyTwoWeekLow
        if (low == null || low.isNaN() || low <= 0.0) {
            return@withContext null
        }
        low
    }

    suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot? = withContext(Dispatchers.IO) {
        val meta: Meta = quoteMeta(symbol) ?: run {
            Log.e(TAG, "No meta found in stock detail response")
            return@withContext null
        }
        val currency: String? = meta.currency
        val previousClose = (meta.regularMarketPreviousClose ?: meta.chartPreviousClose)
            ?.takeIf { !it.isNaN() && it > 0.0 }
        val changePercent = meta.regularMarketChangePercent?.takeIf { !it.isNaN() }
        StockDetailSnapshot(
            lastPrice = meta.regularMarketPrice?.takeIf { !it.isNaN() && it > 0.0 },
            previousClose = previousClose,
            dailyChangePercent = changePercent,
            week52High = meta.fiftyTwoWeekHigh?.takeIf { !it.isNaN() && it > 0.0 },
            week52Low = meta.fiftyTwoWeekLow?.takeIf { !it.isNaN() && it > 0.0 },
            currency = currency?.takeIf { it.isNotBlank() } ?: CurrencyHelper.getCurrencyFromSymbol(symbol),
            exchangeName = meta.exchangeName?.takeIf { it.isNotBlank() },
            companyName = meta.longName ?: meta.shortName ?: meta.symbol
        )
    }

    suspend fun getIntradayChart(symbol: String, period: ChartPeriod = ChartPeriod.DAY): IntradayChartData? =
        chartCache.getOrLoad(symbol to period, chartTtlMs(period)) { fetchIntradayChart(symbol, period) }

    private suspend fun fetchIntradayChart(symbol: String, period: ChartPeriod): IntradayChartData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching intraday chart")
            val response: YahooFinanceResponse = api.getIntradayChart(symbol, period.range, period.interval)
            if (response.chart?.error != null) {
                Log.e(TAG, "Yahoo API error while fetching intraday chart: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found while fetching intraday chart")
                return@withContext null
            }
            val lastTradeTimestamp = result.meta?.regularMarketTime
            val previousClose = (result.meta?.regularMarketPreviousClose ?: result.meta?.chartPreviousClose)
                ?.takeIf { !it.isNaN() && it > 0.0 }

            val timestamps = result.timestamp
            val closes = result.indicators?.quote?.firstOrNull()?.close
            val paired = if (timestamps != null && closes != null) {
                timestamps.zip(closes).mapNotNull { (ts, price) ->
                    price?.takeIf { !it.isNaN() }?.let { ts to it }
                }
            } else emptyList()

            if (paired.size < 2) {
                val reason = when (result.meta?.instrumentType?.lowercase()) {
                    "cryptocurrency", "crypto" -> "Ingen intradagsdata tillgänglig"
                    else -> "Marknaden stängd"
                }
                Log.w(TAG, "Not enough data points for chart: ${paired.size} — $reason")
                val fallbackPrice = result.meta?.regularMarketPrice
                    ?.takeIf { !it.isNaN() && it > 0.0 }
                    ?: previousClose
                if (fallbackPrice != null) {
                    val intervalSeconds = intervalToSeconds(period.interval)
                    val endTs = lastTradeTimestamp ?: (System.currentTimeMillis() / 1000L)
                    val startTs = (endTs - intervalSeconds).coerceAtLeast(0L)
                    return@withContext IntradayChartData(
                        timestamps = listOf(startTs, endTs),
                        prices = listOf(fallbackPrice, fallbackPrice),
                        previousClose = previousClose,
                        lastTradeTimestamp = lastTradeTimestamp,
                        emptyReason = reason
                    )
                }
                return@withContext IntradayChartData(
                    timestamps = emptyList(),
                    prices = emptyList(),
                    previousClose = previousClose,
                    lastTradeTimestamp = lastTradeTimestamp,
                    emptyReason = reason
                )
            }

            IntradayChartData(
                timestamps = paired.map { it.first },
                prices = paired.map { it.second },
                previousClose = previousClose,
                lastTradeTimestamp = lastTradeTimestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching intraday chart: ${e.message}", e)
            null
        }
    }

    suspend fun getSma(symbol: String, period: Int): Double? =
        smaCache.getOrLoad(symbol to period, SMA_TTL_MS) { computeSma(symbol, period) }

    private suspend fun computeSma(symbol: String, period: Int): Double? = withContext(Dispatchers.IO) {
        val closes = fetchDailyCloses(symbol, period) ?: return@withContext null
        if (closes.size < period) return@withContext null
        closes.takeLast(period).average()
    }

    /**
     * Historisk SMA(period)-serie att rita ovanpå kursgrafen för [chartPeriod] — en punkt per
     * dagsstängning, inte bara det senaste värdet, så linjen kan följa prisets rörelse i stället
     * för att ritas som en rak vågrät linje (jfr. Yahoo Finance).
     */
    suspend fun getSmaSeries(symbol: String, period: Int, chartPeriod: ChartPeriod): List<SmaPoint>? =
        smaSeriesCache.getOrLoad(Triple(symbol, period, chartPeriod), SMA_TTL_MS) {
            computeSmaSeries(symbol, period, chartPeriod)
        }

    private suspend fun computeSmaSeries(symbol: String, period: Int, chartPeriod: ChartPeriod): List<SmaPoint>? =
        withContext(Dispatchers.IO) {
            val neededBars = visibleTradingDaysFor(chartPeriod) + period
            val closes = fetchDailyClosesWithTimestamps(symbol, neededBars) ?: return@withContext null
            if (closes.size < period) return@withContext null
            (period - 1 until closes.size).map { i ->
                val window = closes.subList(i - period + 1, i + 1)
                SmaPoint(timestamp = closes[i].first, value = window.sumOf { it.second } / period)
            }
        }

    /**
     * Historisk RSI-serie för [chartPeriod]. Beräknas på dagsstängningar med extra uppvärmning
     * (Wilders utjämning konvergerar långsamt) som trimmas bort före returen. Null för
     * intradagsperioder — daglig RSI säger inget om en 2m/15m-graf.
     */
    suspend fun getRsiSeries(symbol: String, period: Int, chartPeriod: ChartPeriod): List<RsiPoint>? {
        if (!chartPeriod.supportsIndicators()) return null
        return rsiSeriesCache.getOrLoad(Triple(symbol, period, chartPeriod), SMA_TTL_MS) {
            withContext(Dispatchers.IO) {
                val visible = visibleTradingDaysFor(chartPeriod)
                val closes = fetchDailyClosesWithTimestamps(
                    symbol, visible + TechnicalIndicators.rsiWarmupBars(period)
                ) ?: return@withContext null
                val values = TechnicalIndicators.rsi(closes.map { it.second }, period)
                closes.indices
                    .mapNotNull { i -> values[i]?.let { RsiPoint(closes[i].first, it) } }
                    .takeLast(visible)
                    .takeIf { it.isNotEmpty() }
            }
        }
    }

    /** Historiska Bollinger Bands för [chartPeriod]; null för intradagsperioder (se [getRsiSeries]). */
    suspend fun getBollingerSeries(
        symbol: String,
        period: Int,
        stdDevs: Double,
        chartPeriod: ChartPeriod
    ): List<BollingerPoint>? {
        if (!chartPeriod.supportsIndicators()) return null
        return bollingerSeriesCache.getOrLoad(Triple(symbol, period, chartPeriod) to stdDevs, SMA_TTL_MS) {
            withContext(Dispatchers.IO) {
                val visible = visibleTradingDaysFor(chartPeriod)
                val closes = fetchDailyClosesWithTimestamps(symbol, visible + period)
                    ?: return@withContext null
                val values = TechnicalIndicators.bollinger(closes.map { it.second }, period, stdDevs)
                closes.indices
                    .mapNotNull { i ->
                        values[i]?.let { BollingerPoint(closes[i].first, it.upper, it.middle, it.lower) }
                    }
                    .takeLast(visible)
                    .takeIf { it.isNotEmpty() }
            }
        }
    }

    /** Antal handelsdagar en graf-period ungefär visar — avgör hur långt tillbaka SMA-serien behöver hämtas. */
    private fun visibleTradingDaysFor(chartPeriod: ChartPeriod): Int = when (chartPeriod) {
        ChartPeriod.DAY -> 3
        ChartPeriod.WEEK -> 6
        ChartPeriod.MONTH -> 23
        ChartPeriod.THREE_MONTHS -> 66
        ChartPeriod.SIX_MONTHS -> 132
        ChartPeriod.YEAR -> 253
        ChartPeriod.FIVE_YEARS -> 1260
        ChartPeriod.MAX -> 5040
    }

    /** Hämtar dagsstängningar, senaste först utelämnat (kronologisk ordning), minst [minBars] om tillgängligt. */
    private suspend fun fetchDailyCloses(symbol: String, minBars: Int): List<Double>? =
        fetchDailyClosesWithTimestamps(symbol, minBars)?.map { it.second }

    /** Som [fetchDailyCloses] men behåller stängningstidpunkten — krävs för att bygga en tidsserie. */
    private suspend fun fetchDailyClosesWithTimestamps(symbol: String, minBars: Int): List<Pair<Long, Double>>? {
        val range = when {
            minBars <= 60 -> "3mo"
            minBars <= 120 -> "6mo"
            minBars <= 250 -> "1y"
            minBars <= 500 -> "2y"
            else -> "5y"
        }
        return dailyClosesCache.getOrLoad(symbol to range, SMA_TTL_MS) { fetchDailyClosesForRange(symbol, range) }
    }

    private suspend fun fetchDailyClosesForRange(symbol: String, range: String): List<Pair<Long, Double>>? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching daily closes")
                val response: YahooFinanceResponse = api.getIntradayChart(symbol, range = range, interval = "1d")
                if (response.chart?.error != null) {
                    Log.e(TAG, "Yahoo API error while fetching daily closes: ${response.chart.error.description}")
                    return@withContext null
                }
                val result: Result = response.chart?.result?.firstOrNull() ?: return@withContext null
                val timestamps = result.timestamp
                val closes = result.indicators?.quote?.firstOrNull()?.close
                if (timestamps == null || closes == null) return@withContext null
                timestamps.zip(closes)
                    .mapNotNull { (ts, price) -> price?.takeIf { !it.isNaN() && it > 0.0 }?.let { ts to it } }
                    .takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching daily closes: ${e.message}", e)
                null
            }
        }

    suspend fun getNextEarningsReport(symbol: String): NextEarningsInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching next earnings report")
            YahooFinanceService.getNextEarningsReport(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching next earnings report: ${e.message}", e)
            null
        }
    }

    private companion object {
        private const val TAG: String = "YahooMarketDataService"
        private const val QUOTE_META_TTL_MS = 20_000L // 20 sekunder
        private const val SMA_TTL_MS = 15L * 60L * 1000L // 15 minuter — baseras på dagsstängningar

        /** Dagsgrafen ändras hela tiden under handel; längre perioder ändras långsamt. */
        fun chartTtlMs(period: ChartPeriod): Long = when (period) {
            ChartPeriod.DAY -> 30_000L
            ChartPeriod.WEEK -> 2 * 60_000L
            else -> 15 * 60_000L
        }
    }

    private fun intervalToSeconds(interval: String): Long = when (interval) {
        "1m" -> 60L
        "2m" -> 120L
        "5m" -> 300L
        "15m" -> 900L
        "30m" -> 1_800L
        "60m", "1h" -> 3_600L
        "90m" -> 5_400L
        "1d" -> 86_400L
        "5d" -> 432_000L
        "1wk" -> 604_800L
        "1mo" -> 2_592_000L
        "3mo" -> 7_776_000L
        else -> 3_600L
    }
}
