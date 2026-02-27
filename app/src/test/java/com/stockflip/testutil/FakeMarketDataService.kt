package com.stockflip.testutil

import com.stockflip.MarketDataService
import com.stockflip.StockDetailSnapshot
import com.stockflip.WatchType

class FakeMarketDataService(
    private val pricesBySymbol: Map<String, Double> = emptyMap(),
    private val previousCloseBySymbol: Map<String, Double> = emptyMap(),
    private val currencyBySymbol: Map<String, String> = emptyMap(),
    private val exchangeBySymbol: Map<String, String> = emptyMap(),
    private val companyNameBySymbol: Map<String, String> = emptyMap()
) : MarketDataService {
    override suspend fun getStockPrice(symbol: String): Double? = pricesBySymbol[symbol]

    override suspend fun getPreviousClose(symbol: String): Double? = previousCloseBySymbol[symbol]

    override suspend fun getDailyChangePercent(symbol: String): Double? {
        val price: Double = pricesBySymbol[symbol] ?: return null
        val previousClose: Double = previousCloseBySymbol[symbol] ?: return null
        if (previousClose <= 0.0) return null
        return ((price - previousClose) / previousClose) * 100.0
    }

    override suspend fun getATH(symbol: String): Double? = null

    override suspend fun get52WeekLow(symbol: String): Double? = null

    override suspend fun getCurrency(symbol: String): String? = currencyBySymbol[symbol]

    override suspend fun getExchange(symbol: String): String? = exchangeBySymbol[symbol]

    override suspend fun getCompanyName(symbol: String): String? = companyNameBySymbol[symbol]

    override suspend fun getKeyMetric(symbol: String, metricType: WatchType.MetricType): Double? = null

    override suspend fun getStockDetailSnapshot(symbol: String): StockDetailSnapshot? {
        val lastPrice: Double? = pricesBySymbol[symbol] ?: return null
        return StockDetailSnapshot(
            lastPrice = lastPrice,
            previousClose = previousCloseBySymbol[symbol],
            week52High = null,
            week52Low = null,
            currency = currencyBySymbol[symbol],
            exchangeName = exchangeBySymbol[symbol],
            companyName = companyNameBySymbol[symbol]
        )
    }
}

