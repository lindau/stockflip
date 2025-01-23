import retrofit2.http.GET
import retrofit2.http.Query

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockPrice(@Query("symbol") symbol: String): YahooFinanceResponse
}

data class YahooFinanceResponse(
    val chart: Chart
)

data class Chart(
    val result: List<Result>
)

data class Result(
    val meta: Meta
)

data class Meta(
    val regularMarketPrice: Double
)

