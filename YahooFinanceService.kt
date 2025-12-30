import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.net.CookieManager
import java.net.CookiePolicy
import okhttp3.JavaNetCookieJar

// Modified YahooFinanceService.kt
object YahooFinanceService {
    private const val BASE_URL = "https://query1.finance.yahoo.com/"
    private const val MAX_RETRIES = 3
    
    // CookieManager to handle cookies automatically
    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }
    private val cookieJar = JavaNetCookieJar(cookieManager)

    // Shared OkHttpClient with cookie support
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: YahooFinanceApi = retrofit.create(YahooFinanceApi::class.java)

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
            Log.d("YahooFinanceService", "Fetching cookie...")
            val cookieRequest = Request.Builder()
                .url("https://fc.yahoo.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            // We just need to make the request, the CookieJar will handle the Set-Cookie response
            val cookieResponse = client.newCall(cookieRequest).execute()
            cookieResponse.close()

            // Step 2: Get Crumb
            Log.d("YahooFinanceService", "Fetching crumb...")
            val crumbRequest = Request.Builder()
                .url("https://query1.finance.yahoo.com/v1/test/getcrumb")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val crumbResponse = client.newCall(crumbRequest).execute()
            if (crumbResponse.isSuccessful) {
                val fetchedCrumb = crumbResponse.body?.string()
                if (!fetchedCrumb.isNullOrBlank()) {
                    crumb = fetchedCrumb
                    Log.d("YahooFinanceService", "Successfully fetched crumb: $crumb")
                } else {
                    Log.e("YahooFinanceService", "Crumb response was empty")
                }
            } else {
                Log.e("YahooFinanceService", "Failed to get crumb: ${crumbResponse.code} - ${crumbResponse.message}")
            }
            crumbResponse.close()

        } catch (e: Exception) {
            Log.e("YahooFinanceService", "Error fetching crumb: ${e.message}", e)
        } finally {
            isFetchingCrumb = false
        }
    }

    suspend fun getStockPrice(symbol: String): Double? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                // Not using crumb for simple price yet as it seems to work without it for now, 
                // but if it fails we might need to migrate this too.
                val response = api.getStockPrice(symbol)
                return response.chart.result.firstOrNull()?.meta?.regularMarketPrice
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) {
                    throw e
                }
                delay(1000L * (attempt + 1)) // Exponential backoff
            }
        }
        return null
    }

    suspend fun getATH(symbol: String): Double? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = api.getStockPriceFullHistory(symbol)
                val highs = response.chart.result.firstOrNull()?.indicators?.quote?.firstOrNull()?.high
                return highs?.filterNotNull()?.maxOrNull()
            } catch (e: Exception) {
                if (attempt == MAX_RETRIES - 1) {
                    // Log error or rethrow if crucial, but for now return null to be safe
                     Log.e("YahooFinanceService", "Error fetching ATH for $symbol", e)
                     return null
                }
                delay(1000L * (attempt + 1))
            }
        }
        return null
    }

    suspend fun getDailyHigh(symbol: String): Double? {
        return withContext(Dispatchers.IO) {
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val response = api.getDailyData(symbol)
                    val result = response.chart.result.firstOrNull()
                    val dayHigh = result?.meta?.regularMarketDayHigh
                    if (dayHigh != null) {
                        return@withContext dayHigh
                    }
                    val indicators = result?.indicators?.quote?.firstOrNull()
                    val highs = indicators?.high?.filterNotNull()
                    return@withContext highs?.maxOrNull()
                } catch (e: Exception) {
                    if (attempt == MAX_RETRIES - 1) {
                        Log.e("YahooFinanceService", "Error fetching daily high for $symbol", e)
                        return@withContext null
                    }
                    delay(1000L * (attempt + 1))
                }
            }
            null
        }
    }

    suspend fun getPERatio(symbol: String): Double? {
        return withContext(Dispatchers.IO) {
            try {
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
                if (!response.isSuccessful) {
                    Log.e("YahooFinanceService", "API Error: ${response.code} - ${response.message}")
                    // If 401, maybe crumb expired? Reset for next time
                    if (response.code == 401) {
                        crumb = null
                    }
                    return@withContext null
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e("YahooFinanceService", "Empty response body")
                    return@withContext null
                }
                val jsonObject = JSONObject(responseBody)
                val quoteSummary = jsonObject.optJSONObject("quoteSummary")
                val result = quoteSummary?.optJSONArray("result")?.optJSONObject(0)
                val summaryDetail = result?.optJSONObject("summaryDetail")
                val trailingPE = summaryDetail?.optJSONObject("trailingPE")?.optDouble("raw")
                trailingPE?.takeIf { !it.isNaN() && it > 0 }
            } catch (e: Exception) {
                Log.e("YahooFinanceService", "Error fetching P/E ratio for $symbol: ${e.message}", e)
                null
            }
        }
    }

    suspend fun getPSRatio(symbol: String): Double? {
        return withContext(Dispatchers.IO) {
            try {
                ensureCrumb()
                
                var url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail"
                if (crumb != null) {
                    url += "&crumb=$crumb"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("YahooFinanceService", "API Error: ${response.code} - ${response.message}")
                    if (response.code == 401) {
                        crumb = null
                    }
                    return@withContext null
                }
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e("YahooFinanceService", "Empty response body")
                    return@withContext null
                }
                val jsonObject = JSONObject(responseBody)
                val quoteSummary = jsonObject.optJSONObject("quoteSummary")
                val result = quoteSummary?.optJSONArray("result")?.optJSONObject(0)
                val summaryDetail = result?.optJSONObject("summaryDetail")
                val priceToSalesTrailing12Months = summaryDetail?.optJSONObject("priceToSalesTrailing12Months")?.optDouble("raw")
                priceToSalesTrailing12Months?.takeIf { !it.isNaN() && it > 0 }
            } catch (e: Exception) {
                Log.e("YahooFinanceService", "Error fetching P/S ratio for $symbol: ${e.message}", e)
                null
            }
        }
    }
}