package com.stockflip

import android.app.Application
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager

class StockFlipApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppSecurityManager.init(this)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)
        
        // Initialize WorkManager
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN
            )
            .build()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WorkManager.initialize(this, config)
        StockPriceUpdater.startPeriodicUpdate(this)
        TriggerSeenTracker.init(this)
    }
}
