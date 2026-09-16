package com.stockflip

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.abs

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockPrice(@Path("symbol") symbol: String): YahooFinanceResponse

    @GET("v8/finance/chart/{symbol}")
    suspend fun getIntradayChart(
        @Path("symbol") symbol: String,
        @retrofit2.http.Query("range") range: String = "1d",
        @retrofit2.http.Query("interval") interval: String = "2m"
    ): YahooFinanceResponse
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
    val high: List<Double?>? = null,
    val close: List<Double?>? = null
)

object YahooFinanceService : MarketDataService {
    private const val TAG = "YahooFinanceService"
    private const val BASE_URL = "https://query1.finance.yahoo.com/"
    private const val SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search"

    // CookieManager to handle cookies automatically
    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    }
    private val cookieJar = JavaNetCookieJar(cookieManager)
    
    // Shared OkHttpClient with cookie support
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
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
    private const val ALL_TIME_HIGH_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    private data class CachedAllTimeHigh(val value: Double, val timestamp: Long)
    private val allTimeHighCache = mutableMapOf<String, CachedAllTimeHigh>()

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
                Log.w(TAG, "Cookie request timed out after 2 seconds, continuing without cookie")
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
                        Log.d(TAG, "Successfully fetched Yahoo crumb")
                    } else {
                        Log.e(TAG, "Crumb response was empty")
                    }
                } else {
                    Log.e(TAG, "Failed to get crumb: ${crumbResponse.code} - ${crumbResponse.message}")
                }
                crumbResponse.close()
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Crumb request timed out after 2 seconds")
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

    // getKeyMetric och getAllKeyMetrics anropar samma quoteSummary-endpoint och parsade
    // tidigare samma JSON två gånger (dubbla nätverksanrop för en KeyMetrics-bevakning).
    // getKeyMetric delegerar nu till getAllKeyMetrics och plockar ut ett fält.
    override suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? {
        val metrics = getAllKeyMetrics(symbol) ?: return null
        return when (metricType) {
            WatchType.MetricType.PE_RATIO -> metrics.peRatio
            WatchType.MetricType.PS_RATIO -> metrics.psRatio
            WatchType.MetricType.DIVIDEND_YIELD -> metrics.dividendYield
            WatchType.MetricType.EARNINGS_PER_SHARE -> metrics.earningsPerShare
        }
    }

    override suspend fun getATH(symbol: String): Double? {
        return chartMarketDataService.getATH(symbol)
    }

    override suspend fun getAllTimeHigh(symbol: String): Double? {
        val now = System.currentTimeMillis()
        val cachedValue = synchronized(allTimeHighCache) {
            allTimeHighCache[symbol]?.takeIf { now - it.timestamp < ALL_TIME_HIGH_CACHE_TTL_MS }?.value
        }
        if (cachedValue != null) return cachedValue

        val allTimeHigh = chartMarketDataService.getAllTimeHigh(symbol) ?: return null
        synchronized(allTimeHighCache) {
            allTimeHighCache[symbol] = CachedAllTimeHigh(allTimeHigh, now)
        }
        return allTimeHigh
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

    override suspend fun getIntradayChart(symbol: String, period: ChartPeriod): IntradayChartData? {
        return chartMarketDataService.getIntradayChart(symbol, period)
    }

    override suspend fun getAllKeyMetrics(symbol: String): KeyMetrics? = withContext(Dispatchers.IO) {
        try {
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                val timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime
                if (timeSinceLastFailure < FAILURE_COOLDOWN_MS) {
                    throw Exception("Circuit breaker open")
                } else {
                    consecutiveFailures = 0
                }
            }

            ensureCrumb()
            var url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail,defaultKeyStatistics,financialData"
            if (crumb != null) url += "&crumb=$crumb"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val result = JSONObject(body)
                        .optJSONObject("quoteSummary")
                        ?.optJSONArray("result")
                        ?.optJSONObject(0)
                    val summaryDetail = result?.optJSONObject("summaryDetail")
                    val defaultKeyStatistics = result?.optJSONObject("defaultKeyStatistics")
                    val financialData = result?.optJSONObject("financialData")

                    if (summaryDetail != null || defaultKeyStatistics != null || financialData != null) {
                        val pe = summaryDetail?.optJSONObject("trailingPE")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
                            ?: summaryDetail?.optJSONObject("forwardPE")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
                        val ps = summaryDetail?.optJSONObject("priceToSalesTrailing12Months")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
                        // Prova dividendYield först, sedan trailingAnnualDividendYield (vanligt för icke-amerikanska aktier)
                        val yieldRaw = summaryDetail?.optJSONObject("dividendYield")?.optDouble("raw")
                            ?.takeIf { !it.isNaN() && it > 0 }
                            ?: summaryDetail?.optJSONObject("trailingAnnualDividendYield")?.optDouble("raw")
                                ?.takeIf { !it.isNaN() && it > 0 }
                        val dividendYield = yieldRaw?.let { it * 100 }
                        val marketCap = summaryDetail?.optJSONObject("marketCap")?.optDouble("raw")
                            ?.takeIf { !it.isNaN() && it > 0 }
                        val returnOnEquityRaw = financialData?.optJSONObject("returnOnEquity")?.optDouble("raw")
                            ?.takeIf { !it.isNaN() }
                        val returnOnEquity = returnOnEquityRaw?.let { if (abs(it) <= 1.0) it * 100 else it }
                        val earningsPerShare = defaultKeyStatistics?.optJSONObject("trailingEps")?.optDouble("raw")
                            ?.takeIf { !it.isNaN() && it > 0 }
                            ?: defaultKeyStatistics?.optJSONObject("forwardEps")?.optDouble("raw")
                                ?.takeIf { !it.isNaN() && it > 0 }

                        response.close()
                        return@withContext KeyMetrics(
                            peRatio = pe,
                            psRatio = ps,
                            dividendYield = dividendYield,
                            earningsPerShare = earningsPerShare,
                            marketCap = marketCap,
                            returnOnEquity = returnOnEquity
                        )
                    }
                }
            } else if (response.code == 401) {
                crumb = null
            }
            response.close()
        } catch (e: Exception) {
            if (e.message != "Circuit breaker open") {
                consecutiveFailures++
                lastFailureTime = System.currentTimeMillis()
            }
            Log.w(TAG, "Yahoo getAllKeyMetrics failed: ${e.message}")
            null
        }

        null
    }

    override suspend fun getNextEarningsReport(symbol: String): NextEarningsInfo? = withContext(Dispatchers.IO) {
        try {
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                val timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime
                if (timeSinceLastFailure < FAILURE_COOLDOWN_MS) {
                    throw Exception("Circuit breaker open")
                } else {
                    consecutiveFailures = 0
                }
            }

            ensureCrumb()
            var url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=earnings"
            if (crumb != null) url += "&crumb=$crumb"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val earnings = JSONObject(body)
                        .optJSONObject("quoteSummary")
                        ?.optJSONArray("result")
                        ?.optJSONObject(0)
                        ?.optJSONObject("earnings")

                    if (earnings != null) {
                        val earningsChart = earnings.optJSONObject("earningsChart")
                        val earningsDate = earningsChart?.optJSONArray("earningsDate")?.optJSONObject(0)?.optLong("raw")

                        if (earningsDate != null && earningsDate > 0L) {
                            val currentQuarter = earningsChart?.optString("currentQuarterEstimate", null)
                            val isAnnualReport = currentQuarter?.equals("4q", ignoreCase = true) ?: false

                            response.close()
                            return@withContext NextEarningsInfo(
                                reportDateMillis = earningsDate * 1000L,
                                isAnnualReport = isAnnualReport
                            )
                        }
                    }
                }
            } else if (response.code == 401) {
                crumb = null
            }
            response.close()
        } catch (e: Exception) {
            if (e.message != "Circuit breaker open") {
                consecutiveFailures++
                lastFailureTime = System.currentTimeMillis()
            }
            Log.w(TAG, "Yahoo getNextEarningsReport failed: ${e.message}")
            null
        }

        null
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

            Log.d(TAG, "Searching crypto results for query length ${query.length}")

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
            
            Log.d(TAG, "Received crypto response (${responseBody.length} chars)")
            
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
            
            Log.d(TAG, "Found ${results.size} crypto search results")
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

            // Sök aktier och krypto parallellt i stället för sekventiellt.
            val allResults = coroutineScope {
                val equityDeferred = async { searchEquities(query) }
                val cryptoDeferred = if (includeCrypto) async { searchCrypto(query) } else null
                val results = mutableListOf<StockSearchResult>()
                results.addAll(equityDeferred.await())
                cryptoDeferred?.let { results.addAll(it.await()) }
                results
            }

            Log.d(TAG, "Found ${allResults.size} total search results")
            allResults

        } catch (e: Exception) {
            Log.e(TAG, "Error searching stocks: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun searchEquities(query: String): List<StockSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

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

        Log.d(TAG, "Searching stock results for query length ${query.length}")

        val equityRequest = okhttp3.Request.Builder()
            .url(equityUrl)
            .addHeader("User-Agent", "Mozilla/5.0")
            .build()

        val equityResponse = client.newCall(equityRequest).execute()

        val results = mutableListOf<StockSearchResult>()

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
                                results.add(
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

        return results
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
