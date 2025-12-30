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
import java.net.CookieManager
import java.net.CookiePolicy
import okhttp3.JavaNetCookieJar
import okhttp3.Request
import kotlinx.coroutines.delay

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
    val regularMarketPreviousClose: Double? = null,
    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,
    val regularMarketDayHigh: Double? = null,
    val regularMarketDayLow: Double? = null
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

    // CookieManager to handle cookies automatically
    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }
    private val cookieJar = JavaNetCookieJar(cookieManager)
    
    // Shared OkHttpClient with cookie support
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(YahooFinanceApi::class.java)

    private var crumb: String? = null
    private var isFetchingCrumb = false

    private suspend fun ensureCrumb() {
        if (crumb != null) return
        if (isFetchingCrumb) {
            // Simple wait if another request is already fetching
            var retries = 0
            while (crumb == null && isFetchingCrumb && retries < 20) {
                delay(100)
                retries++
            }
            return
        }

        isFetchingCrumb = true
        try {
            // Step 1: Get Cookie from main page
            Log.d(TAG, "Fetching cookie...")
            val cookieRequest = Request.Builder()
                .url("https://fc.yahoo.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            // We just need to make the request, the CookieJar will handle the Set-Cookie response
            val cookieResponse = client.newCall(cookieRequest).execute()
            cookieResponse.close()

            // Step 2: Get Crumb
            Log.d(TAG, "Fetching crumb...")
            val crumbRequest = Request.Builder()
                .url("https://query1.finance.yahoo.com/v1/test/getcrumb")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val crumbResponse = client.newCall(crumbRequest).execute()
            if (crumbResponse.isSuccessful) {
                val fetchedCrumb = crumbResponse.body?.string()
                if (!fetchedCrumb.isNullOrBlank()) {
                    crumb = fetchedCrumb
                    Log.d(TAG, "Successfully fetched crumb: $crumb")
                } else {
                    Log.e(TAG, "Crumb response was empty")
                }
            } else {
                Log.e(TAG, "Failed to get crumb: ${crumbResponse.code} - ${crumbResponse.message}")
            }
            crumbResponse.close()

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching crumb: ${e.message}", e)
        } finally {
            isFetchingCrumb = false
        }
    }

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

    suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? = withContext(Dispatchers.IO) {
        // Try Yahoo Finance first
        try {
            Log.d(TAG, "Fetching key metric ${metricType.name} for symbol: $symbol from Yahoo Finance")
            ensureCrumb()
            
            // Construct URL with crumb if available
            var url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail"
            if (crumb != null) {
                url += "&crumb=$crumb"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
                
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val quoteSummary = jsonObject.optJSONObject("quoteSummary")
                    val result = quoteSummary?.optJSONArray("result")?.optJSONObject(0)
                    val summaryDetail = result?.optJSONObject("summaryDetail")
                    
                    if (summaryDetail != null) {
                        val value = when (metricType) {
                            WatchType.MetricType.PE_RATIO -> {
                                val pe = summaryDetail.optJSONObject("trailingPE")?.optDouble("raw")
                                if (pe == null || pe.isNaN()) {
                                    summaryDetail.optJSONObject("forwardPE")?.optDouble("raw")
                                } else pe
                            }
                            WatchType.MetricType.PS_RATIO -> {
                                summaryDetail.optJSONObject("priceToSalesTrailing12Months")?.optDouble("raw")
                            }
                            WatchType.MetricType.DIVIDEND_YIELD -> {
                                val yield = summaryDetail.optJSONObject("dividendYield")?.optDouble("raw")
                                // Yahoo returns decimal (e.g. 0.05 for 5%), convert to percentage if needed
                                // Assuming the app expects percentage (e.g. 5.0) based on Finnhub logic
                                if (yield != null && !yield.isNaN()) yield * 100 else null
                            }
                        }
                        
                        if (value != null && !value.isNaN() && value > 0) {
                            Log.d(TAG, "Successfully fetched ${metricType.name} for $symbol from Yahoo: $value")
                            return@withContext value
                        }
                    }
                }
            } else {
                Log.w(TAG, "Yahoo Finance API Error: ${response.code} - ${response.message}")
                 // If 401, maybe crumb expired? Reset for next time
                if (response.code == 401) {
                    crumb = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key metric ${metricType.name} for $symbol from Yahoo: ${e.message}")
        }

        // Fallback to Finnhub
        try {
            Log.d(TAG, "Falling back to Finnhub for key metric ${metricType.name} for symbol: $symbol")
            val result = FinnhubService.getKeyMetric(symbol, metricType)
            if (result == null) {
                Log.w(TAG, "Finnhub returned null for ${metricType.name} for $symbol")
            } else {
                Log.d(TAG, "Finnhub returned ${metricType.name} for $symbol: $result")
            }
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key metric ${metricType.name} for $symbol from Finnhub: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun getATH(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching ATH for symbol: $symbol")
            val response = api.getStockPrice(symbol)
            
            if (response.chart?.error != null) {
                Log.e(TAG, "API Error for $symbol: ${response.chart.error.description}")
                return@withContext null
            }
            
            val result = response.chart?.result?.firstOrNull()
            if (result == null) {
                Log.e(TAG, "No result found for $symbol")
                return@withContext null
            }
            
            val meta = result.meta
            if (meta == null) {
                Log.e(TAG, "No meta found for $symbol")
                return@withContext null
            }
            
            // Try to get 52-week high first (this is the ATH)
            val week52High = meta.fiftyTwoWeekHigh
            if (week52High != null && !week52High.isNaN() && week52High > 0) {
                Log.d(TAG, "Got 52-week high (ATH) for $symbol: $week52High")
                return@withContext week52High
            }
            
            // Fallback: try to get day high
            val dayHigh = meta.regularMarketDayHigh
            if (dayHigh != null && !dayHigh.isNaN() && dayHigh > 0) {
                Log.d(TAG, "Got day high for $symbol: $dayHigh (using as fallback ATH)")
                return@withContext dayHigh
            }
            
            Log.w(TAG, "Could not find ATH for $symbol")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ATH for $symbol: ${e.message}", e)
            null
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