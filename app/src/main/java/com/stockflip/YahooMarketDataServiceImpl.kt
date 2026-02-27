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
            val exchange: String? = result.meta?.exchangeName
            val fallbackCurrency: String = CurrencyHelper.getCurrencyFromExchange(exchange)
            fallbackCurrency
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
            val fallbackCurrency: String? = if (currency.isNullOrBlank()) {
                CurrencyHelper.getCurrencyFromExchange(meta.exchangeName)
            } else {
                null
            }
            StockDetailSnapshot(
                lastPrice = meta.regularMarketPrice?.takeIf { !it.isNaN() && it > 0.0 },
                previousClose = meta.regularMarketPreviousClose?.takeIf { !it.isNaN() && it > 0.0 },
                week52High = meta.fiftyTwoWeekHigh?.takeIf { !it.isNaN() && it > 0.0 },
                week52Low = meta.fiftyTwoWeekLow?.takeIf { !it.isNaN() && it > 0.0 },
                currency = currency?.takeIf { it.isNotBlank() } ?: fallbackCurrency,
                exchangeName = meta.exchangeName?.takeIf { it.isNotBlank() },
                companyName = meta.longName ?: meta.shortName ?: meta.symbol
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stock detail snapshot for $symbol: ${e.message}", e)
            null
        }
    }

    private companion object {
        private const val TAG: String = "YahooMarketDataService"
    }
}

