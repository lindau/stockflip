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
    val regularMarketDayLow: Double? = null,
    val regularMarketChangePercent: Double? = null,
    val chartPreviousClose: Double? = null,
)

data class Indicators(
    val quote: List<Quote>? = null
)

data class Quote(
    val close: List<Double>? = null
)

object YahooFinanceService : MarketDataService {
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
    
    // Separate client with shorter timeout for cookie/crumb fetching
    private val cookieClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(2, TimeUnit.SECONDS) // Reduced from 5s to 2s
        .readTimeout(2, TimeUnit.SECONDS) // Reduced from 5s to 2s
        .writeTimeout(2, TimeUnit.SECONDS) // Reduced from 5s to 2s
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(YahooFinanceApi::class.java)
    private val chartMarketDataService: YahooMarketDataServiceImpl = YahooMarketDataServiceImpl(api)

    private var crumb: String? = null
    private var isFetchingCrumb = false
    
    // Circuit breaker for Yahoo Finance
    private var consecutiveFailures = 0
    private const val MAX_CONSECUTIVE_FAILURES = 3
    private var lastFailureTime = 0L
    private const val FAILURE_COOLDOWN_MS = 60000L // 1 minute cooldown after failures

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
            // Step 1: Get Cookie from main page (optional - skip if it fails)
            try {
                Log.d(TAG, "Fetching cookie...")
                val cookieRequest = Request.Builder()
                    .url("https://fc.yahoo.com")
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                
                Log.d(TAG, "About to execute cookie request to https://fc.yahoo.com")
                val cookieResponse = cookieClient.newCall(cookieRequest).execute()
                Log.d(TAG, "Cookie response received: ${cookieResponse.code} ${cookieResponse.message}")
                
                if (cookieResponse.isSuccessful || cookieResponse.code == 404) {
                    // 404 is acceptable - cookie might already be set or URL changed
                    Log.d(TAG, "Cookie request completed (status: ${cookieResponse.code})")
                } else {
                    Log.w(TAG, "Cookie request failed with status: ${cookieResponse.code}, continuing anyway")
                }
                cookieResponse.close()
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "Cookie request timed out after 5 seconds, continuing without cookie")
            } catch (e: Exception) {
                Log.w(TAG, "Cookie request failed: ${e.message}, continuing anyway")
            }

            // Step 2: Get Crumb (required)
            Log.d(TAG, "Fetching crumb...")
            val crumbRequest = Request.Builder()
                .url("https://query1.finance.yahoo.com/v1/test/getcrumb")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            try {
                Log.d(TAG, "About to execute crumb request to https://query1.finance.yahoo.com/v1/test/getcrumb")
                val crumbResponse = cookieClient.newCall(crumbRequest).execute()
                Log.d(TAG, "Crumb response received: ${crumbResponse.code} ${crumbResponse.message}")
                
                if (crumbResponse.isSuccessful) {
                    val fetchedCrumb = crumbResponse.body?.string()
                    if (!fetchedCrumb.isNullOrBlank()) {
                        crumb = fetchedCrumb.trim()
                        Log.d(TAG, "Successfully fetched crumb: $crumb")
                    } else {
                        Log.e(TAG, "Crumb response was empty")
                    }
                } else {
                    Log.e(TAG, "Failed to get crumb: ${crumbResponse.code} - ${crumbResponse.message}")
                }
                crumbResponse.close()
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Crumb request timed out after 5 seconds")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching crumb: ${e.message}", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in ensureCrumb: ${e.message}", e)
        } finally {
            isFetchingCrumb = false
        }
    }

    override suspend fun getStockPrice(symbol: String): Double? {
        return chartMarketDataService.getStockPrice(symbol)
    }
    
    override suspend fun getCompanyName(symbol: String): String? {
        return chartMarketDataService.getCompanyName(symbol)
    }

    /**
     * Hämtar valuta för en aktie.
     * 
     * @param symbol Aktiens symbol
     * @return Valuta-kod (t.ex. "SEK", "USD"), eller null om det inte kunde hämtas
     */
    override suspend fun getCurrency(symbol: String): String? {
        return chartMarketDataService.getCurrency(symbol)
    }

    /**
     * Hämtar börs för en aktie.
     * 
     * @param symbol Aktiens symbol
     * @return Börs-kod (t.ex. "STO", "NASDAQ"), eller null om det inte kunde hämtas
     */
    override suspend fun getExchange(symbol: String): String? {
        return chartMarketDataService.getExchange(symbol)
    }

    override suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? = withContext(Dispatchers.IO) {
        // Try Yahoo Finance first, unless circuit breaker is open
        try {
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                val timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime
                if (timeSinceLastFailure < FAILURE_COOLDOWN_MS) {
                    Log.w(TAG, "Yahoo Finance circuit breaker open (failures: $consecutiveFailures), skipping to fallback")
                    throw Exception("Circuit breaker open")
                } else {
                    Log.i(TAG, "Circuit breaker cooldown expired, retrying Yahoo Finance")
                    consecutiveFailures = 0
                }
            }
            
            Log.d(TAG, "Fetching key metric ${metricType.name} for symbol: $symbol from Yahoo Finance")
            ensureCrumb()
            
            if (crumb == null) {
                Log.w(TAG, "Crumb is null after ensureCrumb(), will try without crumb (may get 401)")
            }
            
            // Construct URL with crumb if available
            var url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail"
            if (crumb != null) {
                url += "&crumb=$crumb"
                Log.d(TAG, "Using crumb in request: $crumb")
            } else {
                Log.d(TAG, "Making request without crumb (may fail with 401)")
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
                
            Log.d(TAG, "Making request to Yahoo Finance API: $url")
            val response = client.newCall(request).execute()
            Log.d(TAG, "Yahoo Finance API response received: ${response.code} ${response.message}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")
                
                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val quoteSummary = jsonObject.optJSONObject("quoteSummary")
                    val result = quoteSummary?.optJSONArray("result")?.optJSONObject(0)
                    val summaryDetail = result?.optJSONObject("summaryDetail")
                    
                    if (summaryDetail != null) {
                        Log.d(TAG, "Found summaryDetail in response")
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
                        } else {
                            Log.w(TAG, "Could not extract ${metricType.name} from Yahoo response (value: $value)")
                        }
                    } else {
                        Log.w(TAG, "No summaryDetail found in Yahoo response. Result: ${result != null}, quoteSummary: ${quoteSummary != null}")
                    }
                } else {
                    Log.w(TAG, "Yahoo Finance response body is null")
                }
            } else {
                Log.w(TAG, "Yahoo Finance API Error: ${response.code} - ${response.message}")
                 // If 401, maybe crumb expired? Reset for next time
                if (response.code == 401) {
                    Log.w(TAG, "Yahoo Finance returned 401, resetting crumb")
                    crumb = null
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key metric ${metricType.name} for $symbol from Yahoo: ${e.message}")
            if (e.message != "Circuit breaker open") {
                consecutiveFailures++
                lastFailureTime = System.currentTimeMillis()
                Log.w(TAG, "Yahoo failure count: $consecutiveFailures")
            }
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

    override suspend fun getATH(symbol: String): Double? {
        return chartMarketDataService.getATH(symbol)
    }

    /**
     * Hämtar 52-veckors lägsta pris för en aktie.
     * 
     * @param symbol Aktiens symbol
     * @return 52-veckors lägsta pris, eller null om det inte kunde hämtas
     */
    override suspend fun get52WeekLow(symbol: String): Double? {
        return chartMarketDataService.get52WeekLow(symbol)
    }

    /**
     * Hämtar föregående stängningspris för en aktie.
     * Används för att beräkna dagsförändring i procent.
     * 
     * @param symbol Aktiens symbol
     * @return Föregående stängningspris, eller null om det inte kunde hämtas
     */
    override suspend fun getPreviousClose(symbol: String): Double? {
        return chartMarketDataService.getPreviousClose(symbol)
    }

    /**
     * Beräknar dagsförändring i procent för en aktie.
     * Formel: ((currentPrice - previousClose) / previousClose) * 100
     * 
     * @param symbol Aktiens symbol
     * @return Dagsförändring i procent (positivt värde = upp, negativt värde = ned), eller null om det inte kunde beräknas
     */
    override suspend fun getDailyChangePercent(symbol: String): Double? {
        return chartMarketDataService.getDailyChangePercent(symbol)
    }

    override suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot? {
        return chartMarketDataService.getStockDetailSnapshot(symbol)
    }

    @JvmStatic
    suspend fun searchCrypto(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList()
            
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?q=$encodedQuery" +
                "&quotesCount=50" +
                "&lang=en" +
                "&region=US" +
                "&enableFuzzyQuery=false" +
                "&type=cryptocurrency" +
                "&newsCount=0" +
                "&enableEnhancedTrivialQuery=false" +
                "&fields=symbol,shortname,exchange,quoteType,longname,typeDisp,market"

            Log.d(TAG, "Searching crypto with URL: $url")
            
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
            
            Log.d(TAG, "Received crypto response: $responseBody")
            
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
                    
                    if (quoteType == "CRYPTOCURRENCY" || StockSearchResult.isCryptoSymbol(symbol)) {
                        results.add(
                            StockSearchResult(
                                symbol = symbol,
                                name = name,
                                isSwedish = false,
                                isCrypto = true
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Found ${results.size} crypto matching query: $query")
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching crypto: ${e.message}", e)
            emptyList()
        }
    }

    @JvmStatic
    suspend fun searchStocks(query: String, includeCrypto: Boolean = true): List<StockSearchResult> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList()
            
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            // Sök aktier
            val equityUrl = "$SEARCH_URL?q=$encodedQuery" +
                "&quotesCount=50" +
                "&lang=en" +
                "&region=SE" +
                "&enableFuzzyQuery=false" +
                "&type=equity" +
                "&newsCount=0" +
                "&enableEnhancedTrivialQuery=false" +
                "&exchange=STO" +
                "&fields=symbol,shortname,exchange,quoteType,longname,typeDisp,market"

            Log.d(TAG, "Searching stocks with URL: $equityUrl")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
                
            val equityRequest = okhttp3.Request.Builder()
                .url(equityUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
                
            val equityResponse = client.newCall(equityRequest).execute()
            
            val allResults = mutableListOf<StockSearchResult>()
            
            // Parse aktier
            if (equityResponse.isSuccessful) {
                val responseBody = equityResponse.body?.string()
                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val quotes = jsonObject.optJSONArray("quotes")
                    
                    if (quotes != null) {
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
                                    allResults.add(
                                        StockSearchResult(
                                            symbol = symbol,
                                            name = displayName,
                                            isSwedish = symbol.endsWith(".ST") || exchange == "STO",
                                            isCrypto = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Sök krypto om includeCrypto är true
            if (includeCrypto) {
                val cryptoResults = searchCrypto(query)
                allResults.addAll(cryptoResults)
            }
            
            Log.d(TAG, "Found ${allResults.size} total results (stocks + crypto) matching query: $query")
            allResults
            
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
            exchange.isNotEmpty() && exchange != "STO" -> "$name ($exchange)"
            else -> name
        }
    }
} 