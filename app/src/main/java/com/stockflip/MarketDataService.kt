package com.stockflip

/**
 * Snapshot of chart data from a single API response, used for stock detail header
 * so that price and previousClose always come from the same response.
 */
data class StockDetailSnapshot(
    val lastPrice: Double?,
    val previousClose: Double?,
    val dailyChangePercent: Double?,
    val week52High: Double?,
    val week52Low: Double?,
    val currency: String?,
    val exchangeName: String?,
    val companyName: String?
)

/**
 * Nyckeltal för en aktie (P/E, P/S, Direktavkastning, Vinst/aktie, börsvärde, ROE, P/B, EV/EBITDA, skuldsättningsgrad, analytikernas kursmål och rekommendation).
 */
data class KeyMetrics(
    val peRatio: Double?,
    val psRatio: Double?,
    val dividendYield: Double?,
    val earningsPerShare: Double? = null,
    val marketCap: Double? = null,
    val returnOnEquity: Double? = null,
    val priceToBook: Double? = null,
    val evToEbitda: Double? = null,
    /** Skulder/eget kapital i procent (Yahoos debtToEquity), t.ex. 45,2 = 45,2 %. */
    val debtToEquity: Double? = null,
    /** Analytikernas kursmål (Yahoo financialData), i [financialCurrency]. */
    val targetMeanPrice: Double? = null,
    val targetHighPrice: Double? = null,
    val targetLowPrice: Double? = null,
    val analystCount: Int? = null,
    /** Yahoos recommendationKey i gemener: strong_buy, buy, hold, underperform, sell. Null om saknas. */
    val recommendationKey: String? = null,
    val financialCurrency: String? = null
)

/**
 * Information om nästa rapportdatum för en aktie.
 *
 * @param reportDateMillis Datum för nästa rapport i millisekunder sedan epoch
 * @param isAnnualReport true = bokslutskommuniké/årsrapport, false = kvartalsrapport
 */
data class NextEarningsInfo(
    val reportDateMillis: Long,
    val isAnnualReport: Boolean
)

/**
 * Abstraction for market data retrieval.
 *
 * This enables deterministic tests by injecting a fake implementation.
 */
interface MarketDataService {
    suspend fun getStockPrice(symbol: String): Double?
    suspend fun getPreviousClose(symbol: String): Double?
    suspend fun getDailyChangePercent(symbol: String): Double?

    /**
     * Är kursen från senaste handelsdagen (se [StockMarketScheduler.isQuoteFromLatestSession])?
     * false = aktien har inte handlats sedan en tidigare dag, så dagsrörelsen är gammal.
     * null = okänt.
     */
    suspend fun isQuoteFromToday(symbol: String): Boolean? = null
    suspend fun getATH(symbol: String): Double?
    suspend fun getAllTimeHigh(symbol: String): Double?
    suspend fun get52WeekLow(symbol: String): Double?
    suspend fun getCurrency(symbol: String): String?
    suspend fun getExchange(symbol: String): String?
    suspend fun getCompanyName(symbol: String): String?
    suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double?
    suspend fun getAllKeyMetrics(symbol: String): KeyMetrics?
    suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot?
    suspend fun getIntradayChart(symbol: String, period: ChartPeriod = ChartPeriod.DAY): IntradayChartData?
    suspend fun getNextEarningsReport(symbol: String): NextEarningsInfo?
    suspend fun getSma(symbol: String, period: Int): Double?
    suspend fun getSmaSeries(symbol: String, period: Int, chartPeriod: ChartPeriod): List<SmaPoint>?
    suspend fun getRsiSeries(symbol: String, period: Int, chartPeriod: ChartPeriod): List<RsiPoint>?
    suspend fun getBollingerSeries(symbol: String, period: Int, stdDevs: Double, chartPeriod: ChartPeriod): List<BollingerPoint>?
}
