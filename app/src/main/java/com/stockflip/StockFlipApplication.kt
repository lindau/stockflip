package com.stockflip

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class StockFlipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize WorkManager
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
            
        WorkManager.initialize(this, config)
    }
} 