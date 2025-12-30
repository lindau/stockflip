package com.stockflip

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for fetching key metrics from Finnhub API.
 * Used as fallback when Yahoo Finance quoteSummary endpoint fails (401 errors).
 */
object FinnhubService {
    private const val TAG = "FinnhubService"
    private const val BASE_URL = "https://finnhub.io/api/v1"
    
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
                        Log.w(TAG, "Finnhub API Error for $finnhubSymbol: ${response.code} - ${response.message}")
                        if (finnhubSymbol != symbolVariants.last()) {
                            continue // Try next variant
                        }
                        return@withContext null
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
}
