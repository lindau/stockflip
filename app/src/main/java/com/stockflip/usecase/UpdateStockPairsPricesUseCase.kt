package com.stockflip.usecase

import android.util.Log
import com.stockflip.MarketDataService
import com.stockflip.StockPair
import com.stockflip.StockPairDao

class UpdateStockPairsPricesUseCase(
    private val stockPairDao: StockPairDao,
    private val marketDataService: MarketDataService
) {
    suspend fun executeUpdateStockPairsPrices(): List<StockPair> {
        val pairs: List<StockPair> = stockPairDao.getAllStockPairs()
        if (pairs.isEmpty()) {
            return emptyList()
        }
        val updatedPairs: MutableList<StockPair> = mutableListOf()
        pairs.forEach { pair: StockPair ->
            try {
                val price1: Double? = marketDataService.getStockPrice(pair.ticker1)
                val price2: Double? = marketDataService.getStockPrice(pair.ticker2)
                if (price1 == null || price2 == null) {
                    Log.w(TAG, "Missing price(s) for ${pair.ticker1}-${pair.ticker2}: $price1, $price2")
                    return@forEach
                }
                val updatedPair: StockPair = pair.withCurrentPrices(price1, price2)
                stockPairDao.update(updatedPair)
                updatedPairs.add(updatedPair)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating prices for ${pair.ticker1}-${pair.ticker2}: ${e.message}", e)
            }
        }
        return updatedPairs.toList()
    }

    private companion object {
        private const val TAG: String = "UpdateStockPairsPrices"
    }
}

