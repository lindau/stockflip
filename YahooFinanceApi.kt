import retrofit2.http.GET
import retrofit2.http.Query

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockPrice(@Query("symbol") symbol: String): YahooFinanceResponse

    @GET("v8/finance/chart/{symbol}?range=max&interval=1mo")
    suspend fun getStockPriceFullHistory(@Query("symbol") symbol: String): YahooFinanceResponse
}

data class YahooFinanceResponse(
    val chart: Chart
)

data class Chart(
    val result: List<Result>
)

data class Result(
    val meta: Meta,
    val indicators: Indicators? = null
)

data class Meta(
    val regularMarketPrice: Double,
    val symbol: String
)

data class Indicators(
    val quote: List<Quote>
)

data class Quote(
    val high: List<Double?>
)

