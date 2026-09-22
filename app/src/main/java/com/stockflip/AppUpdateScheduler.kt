package com.stockflip

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.stockflip.workers.AppUpdateCheckWorker
import java.util.concurrent.TimeUnit

/**
 * Schemalägger den periodiska app-uppdateringskontrollen. Eget scheduler-objekt per
 * funktionsområde, samma mönster som StockMarketScheduler bredvid StockPriceUpdater.
 */
object AppUpdateScheduler {
    const val WORK_NAME_PERIODIC = "AppUpdateCheckPeriodic"
    const val WORK_NAME_IMMEDIATE = "AppUpdateCheckImmediate"

    fun schedulePeriodicCheck(context: Context) {
        AppUpdateNotifier.createNotificationChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val initialWork = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .setConstraints(constraints)
            .build()
        val periodicWork = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(WORK_NAME_IMMEDIATE, ExistingWorkPolicy.KEEP, initialWork)
        workManager.enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodicWork)
    }
}
