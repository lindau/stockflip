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
            ACTION_PRICES_UPDATED -> handlePricesUpdated(context)
            else -> Log.w(TAG, "Received unknown action: ${intent.action}")
        }
    }

    /** UI:t (Compose) uppdaterar sig själv via MainViewModel; inget att göra här. */
    private fun handlePricesUpdated(context: Context) {
        Log.d(TAG, "Prices updated broadcast received")
    }

    companion object {
        private const val TAG = "PriceUpdateReceiver"
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"

        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(ACTION_PRICES_UPDATED)
            }
        }
    }
} 