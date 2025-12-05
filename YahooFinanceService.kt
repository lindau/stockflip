import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
}