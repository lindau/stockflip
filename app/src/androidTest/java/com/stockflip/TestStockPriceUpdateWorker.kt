package com.stockflip

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TestStockPriceUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get database instance
            val database = StockPairDatabase.getDatabase(applicationContext)
            val stockPairDao = database.stockPairDao()
            
            // Get all stock pairs
            val stockPairs = stockPairDao.getAllStockPairs()
            
            // Process each stock pair
            stockPairs.forEach { stockPair ->
                // For testing, we'll simulate price updates
                val updatedPair = stockPair.withCurrentPrices(
                    price1 = 100.0,
                    price2 = if (stockPair.notifyWhenEqual) 100.0 else 75.0
                )
                
                // Check notification conditions
                if (shouldSendNotification(updatedPair)) {
                    // Send notification
                    sendTestNotification(updatedPair)
                }
                
                // Update the stock pair in database
                stockPairDao.update(updatedPair)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun shouldSendNotification(pair: StockPair): Boolean {
        return (pair.notifyWhenEqual && kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2) < 0.01) ||
               (pair.priceDifference > 0 && kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2) >= pair.priceDifference)
    }

    private fun sendTestNotification(pair: StockPair) {
        val title = "Stock Price Alert"
        val message = buildNotificationMessage(pair)
        NotificationHelper.showNotification(applicationContext, title, message)
    }

    private fun buildNotificationMessage(pair: StockPair): String {
        val priceDiff = kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2)
        return when {
            pair.notifyWhenEqual && priceDiff < 0.01 -> 
                "${pair.companyName1} and ${pair.companyName2} prices are now equal at ${pair.currentPrice1} SEK"
            priceDiff >= pair.priceDifference -> 
                "Price difference between ${pair.companyName1} and ${pair.companyName2} has reached $priceDiff SEK"
            else -> ""
        }
    }
} 