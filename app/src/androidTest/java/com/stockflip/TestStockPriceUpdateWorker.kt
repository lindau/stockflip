package com.stockflip

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TestStockPriceUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = StockPairDatabase.getDatabase(applicationContext)
            val stockPairDao = database.stockPairDao()
            val stockPairs = stockPairDao.getAllStockPairs()
            
            stockPairs.forEach { pair ->
                // Use the existing prices from the StockPair object
                // This simulates a successful price update without making API calls
                stockPairDao.update(pair)
                
                if (shouldNotify(pair)) {
                    val title = "Stock Price Alert"
                    val message = buildNotificationMessage(pair)
                    showNotification(title, message)
                }
            }

            // Broadcast update
            val intent = Intent(ACTION_PRICES_UPDATED).apply {
                `package` = applicationContext.packageName
            }
            applicationContext.sendBroadcast(intent)
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun shouldNotify(pair: StockPair): Boolean {
        return (pair.notifyWhenEqual && kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2) < 0.01) ||
               (pair.priceDifference > 0 && kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2) >= pair.priceDifference)
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

    private fun showNotification(title: String, message: String) {
        NotificationHelper.showNotification(applicationContext, title, message)
    }

    companion object {
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"
    }
} 