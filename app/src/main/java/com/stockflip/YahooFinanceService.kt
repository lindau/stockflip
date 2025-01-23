package com.stockflip

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

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
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(YahooFinanceApi::class.java)

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
} 