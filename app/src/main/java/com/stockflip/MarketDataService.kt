package com.stockflip

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
}

