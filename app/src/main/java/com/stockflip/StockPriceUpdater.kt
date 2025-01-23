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

    fun startPeriodicUpdate(context: Context) {
        Log.d(TAG, "Setting up periodic price updates")
        
        createNotificationChannel(context)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialWork = OneTimeWorkRequestBuilder<StockPriceUpdateWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<StockPriceUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).also { workManager ->
            workManager.cancelAllWork()
            
            workManager
                .enqueueUniqueWork(
                    WORK_NAME_IMMEDIATE,
                    ExistingWorkPolicy.KEEP,
                    initialWork
                )

            workManager
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )
            
            monitorWorkStatus(workManager)
        }
        
        Log.d(TAG, "Price updates scheduled (both immediate and periodic)")
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