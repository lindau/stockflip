package com.stockflip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class PriceUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast with action: ${intent.action}")
        
        when (intent.action) {
            ACTION_PRICE_UPDATE -> handlePriceUpdate(context)
            ACTION_PRICES_UPDATED -> handlePricesUpdated(context)
            else -> Log.w(TAG, "Received unknown action: ${intent.action}")
        }
    }

    private fun handlePriceUpdate(context: Context) {
        Log.d(TAG, "Handling price update request")
        val alarmManager = StockPriceAlarmManager(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching stock pairs from database")
                val stockPairs = StockPairDatabase.getDatabase(context)
                    .stockPairDao()
                    .getAllStockPairs()
                Log.d(TAG, "Found ${stockPairs.size} stock pairs to check")

                for (pair in stockPairs) {
                    try {
                        Log.d(TAG, "Checking prices for pair: ${pair.companyName1} - ${pair.companyName2}")
                        val price1 = YahooFinanceService.getStockPrice(pair.ticker1)
                        val price2 = YahooFinanceService.getStockPrice(pair.ticker2)

                        if (price1 != null && price2 != null) {
                            Log.d(TAG, "Received prices: ${pair.companyName1}=$price1, ${pair.companyName2}=$price2")
                            if (shouldNotify(pair, price1, price2)) {
                                val title = "Stock Price Alert"
                                val message = buildNotificationMessage(pair, price1, price2)
                                Log.d(TAG, "Showing notification: $message")
                                alarmManager.showNotification(title, message)
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
                context.sendBroadcast(Intent(ACTION_PRICES_UPDATED))
            } catch (e: Exception) {
                Log.e(TAG, "Error checking prices: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun handlePricesUpdated(context: Context) {
        Log.d(TAG, "Handling prices updated notification")
        // This is where the UI should update. The MainActivity observes this broadcast
        // and updates the RecyclerView with the new prices.
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

    companion object {
        private const val TAG = "PriceUpdateReceiver"
        private const val PRICE_EQUALITY_THRESHOLD = 0.01
        const val ACTION_PRICE_UPDATE = "com.stockflip.ACTION_PRICE_UPDATE"
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"

        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(ACTION_PRICE_UPDATE)
                addAction(ACTION_PRICES_UPDATED)
            }
        }
    }
} 