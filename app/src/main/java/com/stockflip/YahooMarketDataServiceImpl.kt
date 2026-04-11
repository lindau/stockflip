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
    private val api: YahooFinanceApi
) {
    suspend fun getStockPrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching price for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val price: Double? = result.meta?.regularMarketPrice
            if (price == null || price.isNaN() || price <= 0.0) {
                Log.e(TAG, "No valid price found for $symbol: $price")
                return@withContext null
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch price for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getCompanyName(symbol: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching company info for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockInfo(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            response.chart?.result?.firstOrNull()?.meta?.let { meta: Meta ->
                meta.longName ?: meta.shortName ?: meta.symbol
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching company info for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getCurrency(symbol: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching currency for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val currency: String? = result.meta?.currency
            if (!currency.isNullOrBlank()) {
                return@withContext currency
            }
            CurrencyHelper.getCurrencyFromSymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching currency for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getExchange(symbol: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching exchange for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val exchange: String? = result.meta?.exchangeName
            if (!exchange.isNullOrBlank()) {
                return@withContext exchange
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching exchange for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getPreviousClose(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching previous close for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val previousClose: Double? = result.meta?.regularMarketPreviousClose
            if (previousClose == null || previousClose.isNaN() || previousClose <= 0.0) {
                Log.w(TAG, "No valid previous close for $symbol: $previousClose")
                return@withContext null
            }
            previousClose
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching previous close for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getDailyChangePercent(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val currentPrice: Double? = result.meta?.regularMarketPrice
            val previousClose: Double? = result.meta?.regularMarketPreviousClose
            if (currentPrice == null || previousClose == null || previousClose <= 0.0) {
                Log.w(TAG, "Cannot compute daily change for $symbol: price=$currentPrice, previousClose=$previousClose")
                return@withContext null
            }
            ((currentPrice - previousClose) / previousClose) * 100.0
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching daily change for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getATH(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching 52w high for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: return@withContext null
            val high: Double? = result.meta?.fiftyTwoWeekHigh
            if (high != null && !high.isNaN() && high > 0.0) {
                return@withContext high
            }
            val dayHigh: Double? = result.meta?.regularMarketDayHigh
            if (dayHigh != null && !dayHigh.isNaN() && dayHigh > 0.0) {
                return@withContext dayHigh
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching 52w high for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun get52WeekLow(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching 52w low for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: return@withContext null
            val low: Double? = result.meta?.fiftyTwoWeekLow
            if (low == null || low.isNaN() || low <= 0.0) {
                return@withContext null
            }
            low
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching 52w low for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching stock detail snapshot for symbol: $symbol")
            val response: YahooFinanceResponse = api.getStockPrice(symbol)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            val meta: Meta = result.meta ?: run {
                Log.e(TAG, "No meta for $symbol")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stock detail snapshot for $symbol: ${e.message}", e)
            null
        }
    }

    suspend fun getIntradayChart(symbol: String, period: ChartPeriod = ChartPeriod.DAY): IntradayChartData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chart for symbol: $symbol period: ${period.range}/${period.interval}")
            val response: YahooFinanceResponse = api.getIntradayChart(symbol, period.range, period.interval)
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for intraday chart $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            val result: Result = response.chart?.result?.firstOrNull() ?: run {
                Log.e(TAG, "No result found for intraday chart $symbol")
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
                Log.w(TAG, "Not enough data points for chart $symbol: ${paired.size} — $reason")
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
            Log.e(TAG, "Error fetching intraday chart for $symbol: ${e.message}", e)
            null
        }
    }

    private companion object {
        private const val TAG: String = "YahooMarketDataService"
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
