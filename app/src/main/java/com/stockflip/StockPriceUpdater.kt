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
import androidx.work.*
import java.util.concurrent.TimeUnit

object StockPriceUpdater {
    private const val TAG = "StockPriceUpdater"
    const val WORK_NAME_PERIODIC = "StockPriceUpdatePeriodic"
    const val WORK_NAME_IMMEDIATE = "StockPriceUpdateImmediate"
    private const val PRICE_EQUALITY_THRESHOLD = 0.01

    fun startPeriodicUpdate(context: Context) {
        Log.d(TAG, "Starting periodic price updates")
        val workManager = WorkManager.getInstance(context)

        // Set up constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create immediate work request for first update
        val initialWork = OneTimeWorkRequestBuilder<StockPriceUpdateWorker>()
            .setConstraints(constraints)
            .build()

        // Create periodic work request for subsequent updates
        val periodicWork = PeriodicWorkRequestBuilder<StockPriceUpdateWorker>(
            1, TimeUnit.MINUTES,  // Repeat interval
            1, TimeUnit.MINUTES   // Flex interval (minimum allowed by WorkManager)
        )
            .setConstraints(constraints)
            .build()

        // Schedule both immediate and periodic work
        workManager.apply {
            // Schedule immediate work
            enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                initialWork
            )

            // Schedule periodic work
            enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }

        // Monitor work status for debugging
        workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME_PERIODIC)
            .observeForever { workInfos ->
                workInfos?.forEach { workInfo ->
                    Log.d(TAG, "Periodic work status: ${workInfo.state}")
                }
            }

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
                "StockFlipChannel",
                "Stock Price Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when stock prices are being updated"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun monitorWorkStatus(workManager: WorkManager) {
        workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME_PERIODIC)
            .observeForever { workInfos ->
                workInfos?.forEach { workInfo ->
                    Log.d(TAG, "Periodic work status: ${workInfo.state}")
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        Log.d(TAG, "Periodic work completed successfully")
                    }
                }
            }

        workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME_IMMEDIATE)
            .observeForever { workInfos ->
                workInfos?.forEach { workInfo ->
                    Log.d(TAG, "Immediate work status: ${workInfo.state}")
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        Log.d(TAG, "Immediate work completed successfully")
                    }
                }
            }
    }
} 