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

// Modified YahooFinanceService.kt
object YahooFinanceService {
    private const val BASE_URL = "https://query1.finance.yahoo.com/"
    private const val MAX_RETRIES = 3

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: YahooFinanceApi = retrofit.create(YahooFinanceApi::class.java)

    suspend fun getStockPrice(symbol: String): Double? {
        repeat(MAX_RETRIES) { attempt ->
            try {
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
                val url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("YahooFinanceService", "API Error: ${response.code} - ${response.message}")
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
                val url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol?modules=summaryDetail"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("YahooFinanceService", "API Error: ${response.code} - ${response.message}")
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