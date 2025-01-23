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
// build.gradle (Module)
plugins {
    id 'androidx.room'
}

android {
    ...
    defaultConfig {
        ...
        javaCompileOptions {
            annotationProcessorOptions {
                arguments = [
                    "room.schemaLocation": "$projectDir/schemas".toString()
                ]
            }
        }
    }
}
}