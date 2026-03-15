package com.stockflip

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object StockPriceUpdater {
    private const val TAG = "StockPriceUpdater"
    const val WORK_NAME_PERIODIC = "StockPriceUpdatePeriodic"
    const val WORK_NAME_IMMEDIATE = "StockPriceUpdateImmediate"
    private const val PRICE_EQUALITY_THRESHOLD = 0.01
    const val CHANNEL_ID = "stock_price_alerts"

    fun startPeriodicUpdate(context: Context) {
        Log.d(TAG, "Starting periodic price updates")
        createNotificationChannel(context)
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val initialWork = OneTimeWorkRequestBuilder<StockPriceUpdateWorker>()
            .setConstraints(constraints)
            .build()
        val periodicWork = PeriodicWorkRequestBuilder<StockPriceUpdateWorker>(
            1, TimeUnit.MINUTES,
            1, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        workManager.apply {
            enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                initialWork
            )
            enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }
        monitorWorkStatus(workManager)
        Log.d(TAG, "Price updates scheduled successfully")
    }

    fun requestBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = activity.packageName
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request battery optimization exemption: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stock Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for stock price alerts"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel: ${channel.id}")
        }
    }

    private fun monitorWorkStatus(workManager: WorkManager) {
        val scope = ProcessLifecycleOwner.get().lifecycleScope
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_PERIODIC).collect { workInfos ->
                workInfos.forEach { workInfo -> Log.d(TAG, "Periodic work status: ${workInfo.state}") }
            }
        }
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_IMMEDIATE).collect { workInfos ->
                workInfos.forEach { workInfo -> Log.d(TAG, "Immediate work status: ${workInfo.state}") }
            }
        }
    }
} 