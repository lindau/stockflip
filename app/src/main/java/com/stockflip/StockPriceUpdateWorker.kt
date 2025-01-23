package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class StockPriceUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val database = StockPairDatabase.getDatabase(context)
    private val stockPairDao = database.stockPairDao()

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting price update check")
        try {
            val stockPairs = stockPairDao.getAllStockPairs()
            Log.d(TAG, "Found ${stockPairs.size} stock pairs to update")

            for (pair in stockPairs) {
                try {
                    Log.d(TAG, "Fetching prices for ${pair.companyName1} and ${pair.companyName2}")
                    val price1 = YahooFinanceService.getStockPrice(pair.ticker1)
                    val price2 = YahooFinanceService.getStockPrice(pair.ticker2)

                    if (price1 != null && price2 != null) {
                        Log.d(TAG, "Received prices: ${pair.companyName1}=$price1, ${pair.companyName2}=$price2")
                        
                        // Update the stock pair with new prices
                        val updatedPair = pair.withCurrentPrices(price1, price2)
                        stockPairDao.update(updatedPair)
                        Log.d(TAG, "Updated prices in database for pair ${pair.id}")

                        if (shouldNotify(pair, price1, price2)) {
                            val title = "Stock Price Alert"
                            val message = buildNotificationMessage(pair, price1, price2)
                            showNotification(title, message)
                        }
                    } else {
                        Log.w(TAG, "Failed to get prices for ${pair.companyName1} or ${pair.companyName2}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking prices for pair ${pair.companyName1} - ${pair.companyName2}: ${e.message}")
                }
            }

            // Broadcast update for UI refresh
            Log.d(TAG, "Broadcasting price update completion")
            applicationContext.sendBroadcast(Intent(ACTION_PRICES_UPDATED))
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Price update worker failed: ${e.message}")
            Result.failure()
        }
    }

    private fun shouldNotify(pair: StockPair, price1: Double, price2: Double): Boolean {
        return (pair.notifyWhenEqual && abs(price1 - price2) < PRICE_EQUALITY_THRESHOLD) ||
               (pair.priceDifference > 0 && abs(price1 - price2) >= pair.priceDifference)
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
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stock Price Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for stock price alerts"
            }
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