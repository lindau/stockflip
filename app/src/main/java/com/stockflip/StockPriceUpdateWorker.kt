package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.delay
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class StockPriceUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        var attempt = 1
        
        while (true) {
            try {
                Log.d(TAG, "Starting price update (attempt $attempt)")
                val database = StockPairDatabase.getDatabase(applicationContext)
                val stockPairDao = database.stockPairDao()
                val yahooFinanceService = YahooFinanceService
                val pairs = stockPairDao.getAllStockPairs()
                var updatedCount = 0
                
                pairs.forEach { pair ->
                    try {
                        Log.d(TAG, "Checking prices for ${pair.ticker1} and ${pair.ticker2}")
                        val price1 = yahooFinanceService.getStockPrice(pair.ticker1)
                        val price2 = yahooFinanceService.getStockPrice(pair.ticker2)
                        
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
                    Log.d(TAG, "Broadcasting price update completion for $updatedCount pairs")
                    val intent = Intent(ACTION_PRICES_UPDATED).apply {
                        `package` = applicationContext.packageName
                    }
                    applicationContext.sendBroadcast(intent)
                } else {
                    Log.w(TAG, "No prices were updated")
                }
                
                // Schedule next update based on market hours
                val interval = StockMarketScheduler.getUpdateInterval()
                scheduleNextUpdate(interval)
                
                return Result.success()
                
            } catch (e: Exception) {
                Log.e(TAG, "Price update worker failed: ${e.message}")
                
                if (StockMarketScheduler.shouldRetry(attempt, e)) {
                    attempt++
                    delay(StockMarketScheduler.RETRY_DELAY_MINUTES * 60 * 1000) // Convert minutes to milliseconds
                    continue
                }
                
                return Result.failure()
            }
        }
    }

    private fun shouldNotify(pair: StockPair, price1: Double, price2: Double): Boolean {
        // Don't notify if either price is 0 (not yet fetched)
        if (price1 == 0.0 || price2 == 0.0) {
            Log.d(TAG, "Skipping notification check for ${pair.ticker1}-${pair.ticker2} because prices are not yet fetched")
            return false
        }
        
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
        // Create an Intent to open MainActivity
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Create PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, StockPriceUpdater.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)  // Add the PendingIntent
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Sent notification: $title - $message")
    }

    private fun scheduleNextUpdate(intervalMinutes: Long) {
        val workRequest = PeriodicWorkRequestBuilder<StockPriceUpdateWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            intervalMinutes / 2, TimeUnit.MINUTES  // Flex period half of the interval
        ).build()
        
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "StockPriceUpdate",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        
        Log.d(TAG, "Scheduled next update with $intervalMinutes minute interval")
    }

    companion object {
        private const val TAG = "StockPriceUpdateWorker"
        private const val PRICE_EQUALITY_THRESHOLD = 0.01
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"
    }
} 