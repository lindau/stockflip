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
 * Nyckeltal för en aktie (P/E, P/S, Direktavkastning).
 */
data class KeyMetrics(
    val peRatio: Double?,
    val psRatio: Double?,
    val dividendYield: Double?
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
    suspend fun getATH(symbol: String): Double?
    suspend fun get52WeekLow(symbol: String): Double?
    suspend fun getCurrency(symbol: String): String?
    suspend fun getExchange(symbol: String): String?
    suspend fun getCompanyName(symbol: String): String?
    suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double?
    suspend fun getAllKeyMetrics(symbol: String): KeyMetrics?
    suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot?
    suspend fun getIntradayChart(symbol: String, period: ChartPeriod = ChartPeriod.DAY): IntradayChartData?
}

