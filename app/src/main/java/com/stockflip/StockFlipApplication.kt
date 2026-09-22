package com.stockflip

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory

class StockFlipApplication : Application(), ImageLoaderFactory {
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

        // WebView.setWebContentsDebuggingEnabled flyttad till MarkdownAssetFragment —
        // laddar WebView-providern (~50-150 ms) bara när manualen/ändringsloggen
        // faktiskt öppnas, i stället för vid varje kallstart.
        WorkManager.initialize(this, config)
        StockPriceUpdater.startPeriodicUpdate(this)
        TriggerSeenTracker.init(this)
        AppUpdateSettings.init(this)
        AppUpdateScheduler.schedulePeriodicCheck(this)
    }

    override fun newImageLoader(): ImageLoader = LogoImageLoader.build(this)
}
