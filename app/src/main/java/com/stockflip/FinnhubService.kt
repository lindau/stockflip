package com.stockflip

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service for fetching key metrics from Finnhub API.
 * Used as fallback when Yahoo Finance quoteSummary endpoint fails (401 errors).
 */
object FinnhubService {
    private const val TAG = "FinnhubService"
    private const val BASE_URL = "https://finnhub.io/api/v1"
    
    // Rate limiting: Finnhub free tier allows 60 calls/minute
    // Add delay between requests to avoid hitting rate limits
    private var lastRequestTime = 0L
    private const val MIN_REQUEST_INTERVAL_MS = 1000L // 1 second between requests (60 requests per minute max)
    
    // API key is read from BuildConfig (which reads from local.properties)
    private val apiKey: String = try {
        val key = BuildConfig.FINNHUB_API_KEY
        Log.d(TAG, "API key loaded from BuildConfig, length: ${key.length}, first 5 chars: ${key.take(5)}...")
        key
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load API key from BuildConfig: ${e.message}", e)
        "" // Fallback for test environment
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Tries multiple symbol formats for Finnhub API.
     * Some stocks might need different formats.
     * For Swedish stocks, we try both with .ST and without.
     */
    private fun getSymbolVariants(symbol: String): List<String> {
        val variants = mutableListOf<String>()
        
        if (symbol.endsWith(".ST")) {
            // Try with .ST first
            variants.add(symbol)
            // Try without .ST
            variants.add(symbol.replace(".ST", ""))
        } else {
            variants.add(symbol)
        }
        
        return variants
    }
    
    /**
     * Fetches key metric from Finnhub API.
     * @param symbol Stock symbol (will try multiple formats if needed)
     * @param metricType Type of metric to fetch
     * @return Metric value or null if not found/error
     */
    suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? = withContext(Dispatchers.IO) {
        try {
            // Check if API key is configured
            val trimmedKey = apiKey.trim().removeSurrounding("\"")
            Log.d(TAG, "API key check - original length: ${apiKey.length}, trimmed length: ${trimmedKey.length}")
            if (trimmedKey.isEmpty()) {
                Log.e(TAG, "Finnhub API key not configured or empty. Please set FINNHUB_API_KEY in local.properties and rebuild")
                Log.e(TAG, "Current apiKey value: '$apiKey'")
                return@withContext null
            }
            
            // Rate limiting: Add delay if needed to avoid hitting rate limits
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                val delayNeeded = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest
                Log.d(TAG, "Rate limiting: Waiting ${delayNeeded}ms before next request")
                delay(delayNeeded)
            }
            lastRequestTime = System.currentTimeMillis()
            
            val symbolVariants = getSymbolVariants(symbol)
            Log.d(TAG, "Fetching ${metricType.name} from Finnhub for symbol: $symbol (trying variants: $symbolVariants)")
            
            // Try each symbol variant until we get a successful response
            for (finnhubSymbol in symbolVariants) {
                try {
                    // Finnhub API endpoint for key metrics
                    // metric=all returns all available metrics including P/E, P/S, dividend yield
                    val url = "$BASE_URL/stock/metric?symbol=$finnhubSymbol&metric=all&token=$trimmedKey"
                    Log.d(TAG, "Calling Finnhub API: $BASE_URL/stock/metric?symbol=$finnhubSymbol&metric=all&token=***")
                    
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Accept", "application/json")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    
                    if (!response.isSuccessful) {
                        when (response.code) {
                            429 -> {
                                Log.w(TAG, "Finnhub API Rate Limit (429) for $finnhubSymbol - Too many requests. Please wait before refreshing.")
                                // Wait a bit longer before returning to avoid immediate retry
                                delay(2000)
                                // Return null to indicate rate limit, but don't try other variants
                                return@withContext null
                            }
                            else -> {
                                Log.w(TAG, "Finnhub API Error for $finnhubSymbol: ${response.code} - ${response.message}")
                                if (finnhubSymbol != symbolVariants.last()) {
                                    continue // Try next variant
                                }
                                return@withContext null
                            }
                        }
                    }
                    
                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        Log.w(TAG, "Empty response body from Finnhub for $finnhubSymbol")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue // Try next variant
                        }
                        return@withContext null
                    }
                    
                    Log.d(TAG, "Finnhub response received for $symbol (variant: $finnhubSymbol), length: ${responseBody.length}")
                    
                    val jsonObject = JSONObject(responseBody)
                    val metric = jsonObject.optJSONObject("metric")
                    
                    if (metric == null) {
                        Log.w(TAG, "No 'metric' object in Finnhub response for $finnhubSymbol")
                        // Log full response for debugging (first 500 chars)
                        val preview = responseBody.take(500)
                        Log.d(TAG, "Response preview: $preview...")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue // Try next variant
                        }
                        return@withContext null
                    }
                    
                    val resultValue = when (metricType) {
                        WatchType.MetricType.PE_RATIO -> {
                            // Try multiple field names for P/E ratio
                            var peRatio = metric.optDouble("peRatio", Double.NaN)
                            if (peRatio.isNaN() || peRatio <= 0) {
                                peRatio = metric.optDouble("peRatioTTM", Double.NaN)
                            }
                            if (peRatio.isNaN() || peRatio <= 0) {
                                peRatio = metric.optDouble("peRatioForward", Double.NaN)
                            }
                            if (peRatio.isNaN() || peRatio <= 0) {
                                peRatio = metric.optDouble("peBasicExclExtraTTM", Double.NaN)
                            }
                            if (peRatio.isNaN() || peRatio <= 0) {
                                peRatio = metric.optDouble("peBasicInclExtraTTM", Double.NaN)
                            }
                            peRatio.takeIf { !it.isNaN() && it > 0 }
                        }
                        WatchType.MetricType.PS_RATIO -> {
                            // Try multiple field names for P/S ratio
                            var psRatio = metric.optDouble("priceToSalesRatio", Double.NaN)
                            if (psRatio.isNaN() || psRatio <= 0) {
                                psRatio = metric.optDouble("priceToSalesRatioTTM", Double.NaN)
                            }
                            if (psRatio.isNaN() || psRatio <= 0) {
                                psRatio = metric.optDouble("psRatio", Double.NaN)
                            }
                            psRatio.takeIf { !it.isNaN() && it > 0 }
                        }
                        WatchType.MetricType.DIVIDEND_YIELD -> {
                            // Try multiple field names for dividend yield
                            var dividendYield = metric.optDouble("dividendYield", Double.NaN)
                            if (dividendYield.isNaN() || dividendYield <= 0) {
                                dividendYield = metric.optDouble("dividendYieldIndicatedAnnual", Double.NaN)
                            }
                            if (dividendYield.isNaN() || dividendYield <= 0) {
                                dividendYield = metric.optDouble("dividendYieldTTM", Double.NaN)
                            }
                            // Finnhub returns as percentage (0-100), so we use as is
                            // But sometimes it might be in decimal format (0-1), so check
                            if (!dividendYield.isNaN() && dividendYield > 0 && dividendYield < 1.0) {
                                dividendYield = dividendYield * 100
                            }
                            dividendYield.takeIf { !it.isNaN() && dividendYield > 0 }
                        }
                    }
                    
                    if (resultValue != null) {
                        Log.d(TAG, "Successfully extracted ${metricType.name} from Finnhub for $symbol (variant: $finnhubSymbol): $resultValue")
                        return@withContext resultValue
                    } else {
                        Log.w(TAG, "Could not find ${metricType.name} in Finnhub response for $finnhubSymbol")
                        // Log available keys for debugging
                        val keys = metric.keys().asSequence().joinToString(", ")
                        Log.d(TAG, "Available keys in metric: $keys")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue // Try next variant
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error trying variant $finnhubSymbol: ${e.message}")
                    if (finnhubSymbol != symbolVariants.last()) {
                        continue // Try next variant
                    }
                    // If this was the last variant, return null
                    return@withContext null
                }
            }
            
            // If we get here, all variants failed
            Log.w(TAG, "All symbol variants failed for $symbol")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key metric ${metricType.name} from Finnhub for $symbol: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Data class för historisk nyckeltal-data.
     */
    data class MetricHistoryData(
        val date: Long, // Timestamp i millisekunder
        val value: Double
    )

    /**
     * Hämtar historisk data för ett nyckeltal.
     * 
     * Finnhub's /stock/financials endpoint ger kvartalsvisa/årliga rapporter.
     * Vi konverterar dessa till dagliga snapshots genom att använda senaste kvartalsvärdet
     * för varje dag fram till nästa kvartal.
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @param years Antal år att hämta (1, 3 eller 5)
     * @return Lista av MetricHistoryData, eller null vid fel
     */
    suspend fun getMetricHistory(
        symbol: String,
        metricType: WatchType.MetricType,
        years: Int = 5
    ): List<MetricHistoryData>? = withContext(Dispatchers.IO) {
        try {
            val trimmedKey = apiKey.trim().removeSurrounding("\"")
            if (trimmedKey.isEmpty()) {
                Log.e(TAG, "Finnhub API key not configured for historical data")
                return@withContext null
            }

            // Rate limiting
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                val delayNeeded = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest
                delay(delayNeeded)
            }
            lastRequestTime = System.currentTimeMillis()

            val symbolVariants = getSymbolVariants(symbol)
            Log.d(TAG, "Fetching historical ${metricType.name} from Finnhub for $symbol (years: $years)")

            for (finnhubSymbol in symbolVariants) {
                try {
                    // Finnhub API endpoint for financials (quarterly/annual reports)
                    val url = "$BASE_URL/stock/financials-reported?symbol=$finnhubSymbol&token=$trimmedKey"
                    Log.d(TAG, "Calling Finnhub API: $BASE_URL/stock/financials-reported?symbol=$finnhubSymbol&token=***")

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Accept", "application/json")
                        .build()

                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        when (response.code) {
                            429 -> {
                                Log.w(TAG, "Finnhub API Rate Limit (429) for historical data")
                                delay(2000)
                                return@withContext null
                            }
                            else -> {
                                Log.w(TAG, "Finnhub API Error for historical data: ${response.code}")
                                if (finnhubSymbol != symbolVariants.last()) {
                                    continue
                                }
                                return@withContext null
                            }
                        }
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        Log.w(TAG, "Empty response body from Finnhub for historical data")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue
                        }
                        return@withContext null
                    }

                    val jsonObject = JSONObject(responseBody)
                    val data = jsonObject.optJSONArray("data")
                    
                    if (data == null || data.length() == 0) {
                        Log.w(TAG, "No financial data in Finnhub response for $finnhubSymbol")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue
                        }
                        return@withContext null
                    }

                    // Parse financial statements and extract metric values
                    val historyData = parseFinancialsForMetric(data, metricType, years)
                    
                    if (historyData.isNotEmpty()) {
                        Log.d(TAG, "Successfully extracted ${historyData.size} historical data points for ${metricType.name}")
                        return@withContext historyData
                    } else {
                        Log.w(TAG, "No historical data extracted for ${metricType.name}")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching historical data for $finnhubSymbol: ${e.message}")
                    if (finnhubSymbol != symbolVariants.last()) {
                        continue
                    }
                }
            }

            Log.w(TAG, "All symbol variants failed for historical data")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metric history: ${e.message}", e)
            null
        }
    }

    /**
     * Parsar financial statements och extraherar nyckeltal-värden.
     * 
     * Finnhub's financials endpoint returnerar kvartalsvisa/årliga rapporter.
     * Vi konverterar dessa till dagliga snapshots.
     */
    private fun parseFinancialsForMetric(
        data: JSONArray,
        metricType: WatchType.MetricType,
        years: Int
    ): List<MetricHistoryData> {
        val historyData = mutableListOf<MetricHistoryData>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.YEAR, -years)
        }

        try {
            // Iterera genom financial statements (senaste först)
            for (i in 0 until data.length()) {
                val statement = data.optJSONObject(i) ?: continue
                val reportDateStr = statement.optString("reportDate", "")
                // reportType could be "10-Q" (quarterly) or "10-K" (annual) - not used currently

                if (reportDateStr.isEmpty()) continue

                try {
                    val reportDate = dateFormat.parse(reportDateStr)
                    if (reportDate == null || reportDate.before(cutoffDate.time)) {
                        continue // Skip reports older than requested period
                    }

                    // Extract metric value from statement
                    val metricValue = extractMetricFromStatement(statement, metricType)
                    if (metricValue == null || metricValue <= 0) {
                        continue
                    }

                    // Convert quarterly/annual report to daily snapshots
                    // Use the report date and create daily entries until next report
                    val reportTimestamp = reportDate.time
                    
                    // Find next report date (if any)
                    var nextReportTimestamp: Long? = null
                    for (j in (i + 1) until data.length()) {
                        val nextStatement = data.optJSONObject(j) ?: continue
                        val nextReportDateStr = nextStatement.optString("reportDate", "")
                        if (nextReportDateStr.isNotEmpty()) {
                            val nextDate = dateFormat.parse(nextReportDateStr)
                            if (nextDate != null) {
                                nextReportTimestamp = nextDate.time
                                break
                            }
                        }
                    }

                    // Create daily snapshots from report date until next report or now
                    val endTimestamp = nextReportTimestamp ?: System.currentTimeMillis()
                    calendar.timeInMillis = reportTimestamp
                    
                    while (calendar.timeInMillis <= endTimestamp && calendar.timeInMillis <= System.currentTimeMillis()) {
                        historyData.add(MetricHistoryData(
                            date = calendar.timeInMillis,
                            value = metricValue
                        ))
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing report date $reportDateStr: ${e.message}")
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing financials: ${e.message}", e)
        }

        // Sort by date (oldest first)
        return historyData.sortedBy { it.date }
    }

    /**
     * Extraherar nyckeltal-värde från en financial statement.
     */
    private fun extractMetricFromStatement(
        statement: JSONObject,
        metricType: WatchType.MetricType
    ): Double? {
        // Finnhub financials structure varies, try common fields
        val metric = statement.optJSONObject("metric") ?: statement
        
        return when (metricType) {
            WatchType.MetricType.PE_RATIO -> {
                // P/E ratio might not be directly in financials, might need to calculate
                // For now, try to find it in metric object
                var peRatio = metric.optDouble("peRatio", Double.NaN)
                if (peRatio.isNaN() || peRatio <= 0) {
                    peRatio = metric.optDouble("peRatioTTM", Double.NaN)
                }
                peRatio.takeIf { !it.isNaN() && it > 0 }
            }
            WatchType.MetricType.PS_RATIO -> {
                var psRatio = metric.optDouble("priceToSalesRatio", Double.NaN)
                if (psRatio.isNaN() || psRatio <= 0) {
                    psRatio = metric.optDouble("priceToSalesRatioTTM", Double.NaN)
                }
                psRatio.takeIf { !it.isNaN() && it > 0 }
            }
            WatchType.MetricType.DIVIDEND_YIELD -> {
                var dividendYield = metric.optDouble("dividendYield", Double.NaN)
                if (dividendYield.isNaN() || dividendYield <= 0) {
                    dividendYield = metric.optDouble("dividendYieldTTM", Double.NaN)
                }
                // Convert to percentage if needed
                if (!dividendYield.isNaN() && dividendYield > 0 && dividendYield < 1.0) {
                    dividendYield = dividendYield * 100
                }
                dividendYield.takeIf { !it.isNaN() && dividendYield > 0 }
            }
        }
    }
}
