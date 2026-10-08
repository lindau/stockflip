package com.stockflip

/** Marknad vars "heta" aktier visas på Marknad-fliken. */
enum class MoverMarket { SWEDEN, US }

/** Vilken lista över heta aktier som visas. */
enum class MoverList {
    GAINERS, LOSERS, MOST_ACTIVE,

    /** Aktier som många söker eller tittar på just nu. Finns bara för USA (Yahoo har ingen svensk lista). */
    TRENDING;

    fun isAvailableFor(market: MoverMarket): Boolean = this != TRENDING || market == MoverMarket.US
}

/** En rad i en lista över heta aktier. [changePercent] är dagsförändring i procent. */
data class MarketMover(
    val symbol: String,
    val name: String,
    val price: Double,
    val changePercent: Double?,
    val volume: Long?,
    val currency: String,
)

/** Källa för listor över heta aktier. Returnerar `null` när hämtningen misslyckas (tom lista = inga träffar). */
interface MarketMoversService {
    suspend fun getMarketMovers(market: MoverMarket, list: MoverList, count: Int = 10): List<MarketMover>?
}
