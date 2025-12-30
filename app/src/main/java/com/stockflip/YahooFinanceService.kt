package com.stockflip

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockPrice(@Path("symbol") symbol: String): YahooFinanceResponse
    
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockInfo(@Path("symbol") symbol: String): YahooFinanceResponse
}

data class YahooFinanceResponse(
    val chart: Chart? = null
)

data class Chart(
    val result: List<Result>? = null,
    val error: YahooError? = null
)

data class YahooError(
    val code: String? = null,
    val description: String? = null
)

data class Result(
    val meta: Meta? = null,
    val timestamp: List<Long>? = null,
    val indicators: Indicators? = null
)

data class Meta(
    val currency: String? = null,
    val symbol: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
    val instrumentType: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val exchangeName: String? = null,
    val regularMarketPreviousClose: Double? = null
)

data class Indicators(
    val quote: List<Quote>? = null
)

data class Quote(
    val close: List<Double>? = null
)

object YahooFinanceService {
    private const val TAG = "YahooFinanceService"
    private const val BASE_URL = "https://query1.finance.yahoo.com/"
    private const val SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(YahooFinanceApi::class.java)
    
    // Cache for crumb and cookie to avoid fetching them every time
    private var cachedCrumb: String? = null
    private var cachedCookie: String? = null
    private var crumbTimestamp: Long = 0
    private val CRUMB_CACHE_DURATION = 3600000L // 1 hour in milliseconds

    suspend fun getStockPrice(symbol: String): Double? {
        return try {
            Log.d(TAG, "Fetching price for symbol: $symbol")
            val response = api.getStockPrice(symbol)
            
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return null
            }
            
            val result = response.chart?.result?.firstOrNull()
            if (result == null) {
                Log.e(TAG, "No result found for $symbol")
                return null
            }
            
            val price = result.meta?.regularMarketPrice
            if (price == null) {
                Log.e(TAG, "No price found for $symbol")
                return null
            }
            
            Log.d(TAG, """
                Stock data for $symbol:
                Price: $price
                Currency: ${result.meta.currency}
                Exchange: ${result.meta.exchangeName}
                Instrument Type: ${result.meta.instrumentType}
                Short Name: ${result.meta.shortName}
                Long Name: ${result.meta.longName}
            """.trimIndent())
            
            price
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch price for $symbol: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    suspend fun getCompanyName(symbol: String): String? {
        return try {
            Log.d(TAG, "Fetching company info for symbol: $symbol")
            val response = api.getStockInfo(symbol)
            
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return null
            }
            
            response.chart?.result?.firstOrNull()?.meta?.let { meta ->
                val name = meta.longName ?: meta.shortName ?: meta.symbol
                Log.d(TAG, """
                    Company info for $symbol:
                    Long name: ${meta.longName}
                    Short name: ${meta.shortName}
                    Exchange: ${meta.exchangeName}
                    Type: ${meta.instrumentType}
                """.trimIndent())
                name
            } ?: run {
                Log.w(TAG, "No company info available for $symbol")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching company info for $symbol: ${e.message}", e)
            null
        }
    }

    private suspend fun getCrumbAndCookie(): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if we have a valid cached crumb
                val now = System.currentTimeMillis()
                if (cachedCrumb != null && cachedCookie != null && (now - crumbTimestamp) < CRUMB_CACHE_DURATION) {
                    Log.d(TAG, "Using cached crumb and cookie")
                    return@withContext Pair(cachedCrumb, cachedCookie)
                }
                
                Log.d(TAG, "Fetching new crumb and cookie from Yahoo Finance")
                
                // Use a cookie manager to handle cookies properly
                val cookieStore = mutableMapOf<String, List<okhttp3.Cookie>>()
                
                val cookieJar = object : okhttp3.CookieJar {
                    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                        cookieStore[url.host] = cookies
                    }
                    
                    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                        // Return cookies for matching domains
                        val matchingCookies = cookieStore.entries
                            .filter { url.host.endsWith(it.key) || it.key.endsWith(url.host) }
                            .flatMap { it.value }
                        return matchingCookies.ifEmpty { 
                            cookieStore.values.flatten() 
                        }
                    }
                }
                
                val cookieClientWithJar = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .cookieJar(cookieJar)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                // Step 1: Get cookie from fc.yahoo.com (even if it returns 404, it sets cookies)
                try {
                    val fcRequest = okhttp3.Request.Builder()
                        .url("https://fc.yahoo.com")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    
                    val fcResponse = cookieClientWithJar.newCall(fcRequest).execute()
                    Log.d(TAG, "fc.yahoo.com response: ${fcResponse.code}")
                    fcResponse.close() // Close to free resources
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get cookie from fc.yahoo.com: ${e.message}")
                }
                
                // Step 2: Also visit finance.yahoo.com to get additional cookies
                try {
                    val financeRequest = okhttp3.Request.Builder()
                        .url("https://finance.yahoo.com/quote/AAPL")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    
                    val financeResponse = cookieClientWithJar.newCall(financeRequest).execute()
                    val htmlBody = financeResponse.body?.string()
                    financeResponse.close()
                    
                    Log.d(TAG, "finance.yahoo.com response: ${financeResponse.code}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get cookie from finance.yahoo.com: ${e.message}")
                }
                
                // Build cookie string from all collected cookies
                val allCookies = cookieStore.values.flatten().distinctBy { "${it.domain}:${it.name}" }
                val cookieString = allCookies.joinToString("; ") { "${it.name}=${it.value}" }
                
                Log.d(TAG, "Got ${allCookies.size} cookies: ${cookieString.take(150)}...")
                
                // Store HTML body for crumb extraction (from finance.yahoo.com if available)
                val htmlBody = try {
                    val financeRequest = okhttp3.Request.Builder()
                        .url("https://finance.yahoo.com/quote/AAPL")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    cookieClientWithJar.newCall(financeRequest).execute().body?.string()
                } catch (e: Exception) {
                    null
                }
                
                // Step 3: Get crumb using the cookie from fc.yahoo.com
                var crumb: String? = null
                if (cookieString.isNotEmpty()) {
                    try {
                        // Try different crumb endpoints
                        val crumbEndpoints = listOf(
                            "https://query2.finance.yahoo.com/v1/test/getcrumb",
                            "https://query1.finance.yahoo.com/v1/test/getcrumb"
                        )
                        
                        for (endpoint in crumbEndpoints) {
                            try {
                                val crumbRequest = okhttp3.Request.Builder()
                                    .url(endpoint)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                    .addHeader("Cookie", cookieString)
                                    .addHeader("Referer", "https://finance.yahoo.com/")
                                    .addHeader("Accept", "text/plain")
                                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                                    .build()
                                
                                val crumbResponse = cookieClientWithJar.newCall(crumbRequest).execute()
                                if (crumbResponse.isSuccessful) {
                                    crumb = crumbResponse.body?.string()?.trim()
                                    if (crumb != null && crumb.isNotEmpty()) {
                                        Log.d(TAG, "Got crumb from endpoint $endpoint: ${crumb.take(20)}...")
                                        break
                                    }
                                } else {
                                    val errorBody = crumbResponse.body?.string()
                                    Log.w(TAG, "Crumb endpoint $endpoint returned ${crumbResponse.code}: $errorBody")
                                }
                                crumbResponse.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get crumb from $endpoint: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get crumb from endpoint: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "No cookies available, cannot get crumb")
                }
                
                // If crumb endpoint failed, try to extract from HTML
                if (crumb == null || crumb.isEmpty()) {
                    try {
                        if (htmlBody != null) {
                            // Try multiple patterns to find crumb in the HTML
                            // Escape curly braces properly in regex
                            val patterns = listOf(
                                Regex("\"crumb\":\"([^\"]+)\""),
                                Regex("crumb\":\"([^\"]+)\""),
                                Regex("\"CrumbStore\":\\{\"crumb\":\"([^\"]+)\""),
                                Regex("window\\.__PRELOADED_STATE__.*?\"crumb\":\"([^\"]+)\""),
                                Regex("root\\.App\\.main.*?\"crumb\":\"([^\"]+)\""),
                                Regex("crumbStore.*?\"crumb\":\"([^\"]+)\"")
                            )
                            
                            for (pattern in patterns) {
                                try {
                                    val match = pattern.find(htmlBody)
                                    if (match != null) {
                                        crumb = match.groupValues.getOrNull(1)
                                        if (crumb != null && crumb.isNotEmpty()) {
                                            Log.d(TAG, "Extracted crumb from HTML using pattern: ${crumb.take(20)}...")
                                            break
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Pattern matching failed: ${e.message}")
                                }
                            }
                            
                            // If still no crumb, try a simpler approach - look for any crumb-like string
                            if (crumb == null || crumb.isEmpty()) {
                                // Try to find crumb in various formats in the HTML
                                val simplePatterns = listOf(
                                    Regex("crumb[=:]\"?([A-Za-z0-9+/=]{20,})"),
                                    Regex("crumb\":\"([^\"]+)\""),
                                    Regex("crumb=([A-Za-z0-9+/=]+)"),
                                    Regex("getCrumb\\(['\"]([^'\"]+)['\"]"),
                                    Regex("crumb['\"]?\\s*[:=]\\s*['\"]?([A-Za-z0-9+/=]{10,})")
                                )
                                
                                for (pattern in simplePatterns) {
                                    try {
                                        val match = pattern.find(htmlBody)
                                        if (match != null) {
                                            crumb = match.groupValues.getOrNull(1)
                                            if (crumb != null && crumb.isNotEmpty() && crumb.length > 10) {
                                                Log.d(TAG, "Extracted crumb using simple pattern: ${crumb.take(20)}...")
                                                break
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Continue to next pattern
                                    }
                                }
                                
                                // Last resort: search for any base64-like string near "crumb"
                                if (crumb == null || crumb.isEmpty()) {
                                    val crumbIndex = htmlBody.indexOf("crumb", ignoreCase = true)
                                    if (crumbIndex >= 0) {
                                        val searchStart = maxOf(0, crumbIndex - 50)
                                        val searchEnd = minOf(htmlBody.length, crumbIndex + 200)
                                        val snippet = htmlBody.substring(searchStart, searchEnd)
                                        Log.d(TAG, "Crumb context snippet: $snippet")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract crumb from HTML: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                if (crumb != null && cookieString.isNotEmpty()) {
                    cachedCrumb = crumb
                    cachedCookie = cookieString
                    crumbTimestamp = now
                    Log.d(TAG, "Successfully fetched crumb and cookie")
                    Pair(crumb, cookieString)
                } else {
                    Log.w(TAG, "Failed to get crumb, will try without it")
                    Pair(null, cookieString)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching crumb and cookie: ${e.message}", e)
                Pair(null, null)
            }
        }
    }

    suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure symbol is properly formatted for Yahoo Finance API
                // Try original symbol first, then with .ST suffix if it doesn't end with it
                val formattedSymbol = if (symbol.endsWith(".ST") || symbol.contains(".")) {
                    symbol
                } else {
                    // Try with .ST suffix for Swedish stocks
                    "$symbol.ST"
                }
                
                Log.d(TAG, "Fetching key metric ${metricType.name} for symbol: $symbol (formatted: $formattedSymbol)")
                
                // Get crumb and cookie
                val (crumb, cookie) = getCrumbAndCookie()
                
                // Build URL with crumb if available
                val baseUrl = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$formattedSymbol?modules=summaryDetail"
                val url = if (crumb != null) {
                    "$baseUrl&crumb=$crumb"
                } else {
                    baseUrl
                }
                
                Log.d(TAG, "Request URL: $url")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                    
                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Referer", "https://finance.yahoo.com/")
                
                if (cookie != null) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
                
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                
                Log.d(TAG, "Response code: ${response.code}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API Error: ${response.code} - ${response.message}")
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Error body: $errorBody")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e(TAG, "Empty response body")
                    return@withContext null
                }
                
                Log.d(TAG, "Response body length: ${responseBody.length}")
                
                val jsonObject = JSONObject(responseBody)
                val quoteSummary = jsonObject.optJSONObject("quoteSummary")
                
                if (quoteSummary == null) {
                    Log.e(TAG, "No quoteSummary in response")
                    Log.d(TAG, "Full response: $responseBody")
                    return@withContext null
                }
                
                val result = quoteSummary.optJSONArray("result")
                if (result == null || result.length() == 0) {
                    Log.e(TAG, "No result array in quoteSummary")
                    Log.d(TAG, "quoteSummary content: ${quoteSummary.toString()}")
                    return@withContext null
                }
                
                val firstResult = result.optJSONObject(0)
                if (firstResult == null) {
                    Log.e(TAG, "First result is null")
                    return@withContext null
                }
                
                val summaryDetail = firstResult.optJSONObject("summaryDetail")
                if (summaryDetail == null) {
                    Log.e(TAG, "No summaryDetail in result")
                    Log.d(TAG, "First result keys: ${firstResult.keys().asSequence().toList()}")
                    return@withContext null
                }
                
                val value = when (metricType) {
                    WatchType.MetricType.PE_RATIO -> {
                        val trailingPE = summaryDetail.optJSONObject("trailingPE")
                        if (trailingPE == null) {
                            Log.w(TAG, "No trailingPE object in summaryDetail")
                            null
                        } else {
                            val rawValue = trailingPE.optDouble("raw", Double.NaN)
                            Log.d(TAG, "P/E ratio raw value: $rawValue")
                            rawValue.takeIf { !it.isNaN() && it > 0 }
                        }
                    }
                    WatchType.MetricType.PS_RATIO -> {
                        val priceToSales = summaryDetail.optJSONObject("priceToSalesTrailing12Months")
                        if (priceToSales == null) {
                            Log.w(TAG, "No priceToSalesTrailing12Months object in summaryDetail")
                            null
                        } else {
                            val rawValue = priceToSales.optDouble("raw", Double.NaN)
                            Log.d(TAG, "P/S ratio raw value: $rawValue")
                            rawValue.takeIf { !it.isNaN() && it > 0 }
                        }
                    }
                    WatchType.MetricType.DIVIDEND_YIELD -> {
                        val dividendYield = summaryDetail.optJSONObject("dividendYield")
                        if (dividendYield == null) {
                            Log.w(TAG, "No dividendYield object in summaryDetail")
                            null
                        } else {
                            val rawValue = dividendYield.optDouble("raw", Double.NaN)
                            Log.d(TAG, "Dividend yield raw value: $rawValue")
                            rawValue.takeIf { !it.isNaN() && it > 0 }?.let { it * 100 } // Convert to percentage
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown metric type: $metricType")
                        null
                    }
                }
                
                if (value != null) {
                    Log.d(TAG, "Successfully fetched ${metricType.name} for $symbol: $value")
                } else {
                    Log.w(TAG, "Could not extract ${metricType.name} value for $symbol from summaryDetail")
                    Log.d(TAG, "summaryDetail keys: ${summaryDetail.keys().asSequence().toList()}")
                }
                
                value
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching key metric ${metricType.name} for $symbol: ${e.message}", e)
                e.printStackTrace()
                null
            }
        }
    }

    @JvmStatic
    suspend fun searchStocks(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList()
            
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?q=$encodedQuery" +
                "&quotesCount=50" +
                "&lang=en" +
                "&region=SE" +
                "&enableFuzzyQuery=false" +
                "&type=equity" +
                "&newsCount=0" +
                "&enableEnhancedTrivialQuery=false" +
                "&exchange=STO" +
                "&fields=symbol,shortname,exchange,quoteType,longname,typeDisp,market"

            Log.d(TAG, "Searching stocks with URL: $url")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
                
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
                
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} - ${response.message}")
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "Received response: $responseBody")
            
            val jsonObject = JSONObject(responseBody)
            val quotes = jsonObject.optJSONArray("quotes") ?: return@withContext emptyList()
            
            val results = mutableListOf<StockSearchResult>()
            for (i in 0 until quotes.length()) {
                val quote = quotes.getJSONObject(i)
                if (quote.has("symbol")) {
                    val quoteType = quote.optString("quoteType", "")
                    val symbol = quote.getString("symbol")
                    val name = quote.optString("shortname") ?: 
                             quote.optString("longname") ?: 
                             symbol
                    val exchange = quote.optString("exchange", "")
                    val typeDisp = quote.optString("typeDisp", "")
                    val market = quote.optString("market", "")
                    
                    if (isValidStock(quoteType, symbol, name, typeDisp)) {
                        val displayName = buildDisplayName(name, exchange, market)
                        results.add(
                            StockSearchResult(
                                symbol = symbol,
                                name = displayName,
                                isSwedish = symbol.endsWith(".ST") || exchange == "STO"
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Found ${results.size} stocks matching query: $query")
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching stocks: ${e.message}", e)
            emptyList()
        }
    }

    private fun isValidStock(quoteType: String, symbol: String, name: String, typeDisp: String): Boolean {
        if (quoteType !in setOf("EQUITY", "")) return false
        if (symbol.contains("^") || symbol.contains("=")) return false
        if (name.contains("Fund", ignoreCase = true)) return false
        if (typeDisp.contains("Fund", ignoreCase = true)) return false
        if (typeDisp.contains("ETF", ignoreCase = true)) return false
        
        return true
    }

    private fun buildDisplayName(name: String, exchange: String, market: String): String {
        return when {
            exchange == "STO" || market.contains("se_market", ignoreCase = true) -> 
                "$name (Stockholmsbörsen)"
            exchange.isNotEmpty() -> "$name ($exchange)"
            else -> name
        }
    }
} 