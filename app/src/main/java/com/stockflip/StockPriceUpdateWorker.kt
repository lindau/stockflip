package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class StockPriceUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting price update check")
            val database = StockPairDatabase.getDatabase(applicationContext)
            val stockPairDao = database.stockPairDao()
            val stockPairs = stockPairDao.getAllStockPairs()
            
            Log.d(TAG, "Found ${stockPairs.size} pairs to check")
            var updatedCount = 0
            
            stockPairs.forEach { pair ->
                try {
                    Log.d(TAG, "Fetching prices for ${pair.companyName1} (${pair.ticker1}) and ${pair.companyName2} (${pair.ticker2})")
                    val price1 = YahooFinanceService.getStockPrice(pair.ticker1)
                    val price2 = YahooFinanceService.getStockPrice(pair.ticker2)

                    if (price1 != null && price2 != null) {
                        Log.d(TAG, "Got prices for ${pair.ticker1}: $price1, ${pair.ticker2}: $price2")
                        val updatedPair = pair.withCurrentPrices(price1, price2)
                        stockPairDao.update(updatedPair)
                        Log.d(TAG, "Updated database with new prices for ${pair.ticker1}-${pair.ticker2}")
                        updatedCount++

                        if (shouldNotify(pair, price1, price2)) {
                            val title = "Stock Price Alert"
                            val message = buildNotificationMessage(pair, price1, price2)
                            showNotification(title, message)
                        }
                    } else {
                        Log.w(TAG, "Failed to get prices for ${pair.ticker1} or ${pair.ticker2}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking prices for pair ${pair.companyName1} - ${pair.companyName2}: ${e.message}")
                }
            }

            if (updatedCount > 0) {
                // Broadcast update for UI refresh
                Log.d(TAG, "Broadcasting price update completion for $updatedCount pairs")
                val intent = Intent(ACTION_PRICES_UPDATED).apply {
                    `package` = applicationContext.packageName
                }
                applicationContext.sendBroadcast(intent)
            } else {
                Log.w(TAG, "No prices were updated")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Price update worker failed: ${e.message}")
            Result.failure()
        }
    }

    private fun shouldNotify(pair: StockPair, price1: Double, price2: Double): Boolean {
        val shouldNotify = (pair.notifyWhenEqual && abs(price1 - price2) < PRICE_EQUALITY_THRESHOLD) ||
               (pair.priceDifference > 0 && abs(price1 - price2) >= pair.priceDifference)
        
        Log.d(TAG, """
            Notification check for ${pair.companyName1} - ${pair.companyName2}:
            Price difference: ${abs(price1 - price2)}
            Notify when equal: ${pair.notifyWhenEqual}
            Price difference threshold: ${pair.priceDifference}
            Should notify: $shouldNotify
        """.trimIndent())
        
        return shouldNotify
    }

    private fun buildNotificationMessage(pair: StockPair, price1: Double, price2: Double): String {
        return when {
            pair.notifyWhenEqual && abs(price1 - price2) < PRICE_EQUALITY_THRESHOLD -> 
                "${pair.companyName1} and ${pair.companyName2} prices are now equal at ${String.format("%.2f", price1)} SEK"
            abs(price1 - price2) >= pair.priceDifference -> 
                "Price difference between ${pair.companyName1} and ${pair.companyName2} has reached ${String.format("%.2f", abs(price1 - price2))} SEK"
            else -> ""
        }
    }

    private fun showNotification(title: String, message: String) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Stock Price Alerts"
            val descriptionText = "Notifications for stock price alerts"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "StockPriceUpdateWorker"
        private const val CHANNEL_ID = "stock_price_alerts"
        private const val PRICE_EQUALITY_THRESHOLD = 0.01
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"
    }
} 