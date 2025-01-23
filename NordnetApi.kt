import retrofit2.http.GET
import retrofit2.http.Query

interface NordnetApi {
    @GET("instruments")
    suspend fun getInstrument(@Query("query") symbol: String): List<Instrument>

    @GET("price")
    suspend fun getPrice(@Query("identifier") identifier: String): Price
}

data class Instrument(
    val instrument_id: String,
    val symbol: String,
    val name: String
)

data class Price(
    val last: Double,
    val high: Double,
    val low: Double,
    val change: Double,
    val change_percent: Double
)

