package com.stockflip.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stockflip.AppUpdateChecker
import com.stockflip.AppUpdateNotifier
import com.stockflip.AppUpdateSettings
import com.stockflip.MainActivity
import com.stockflip.NotificationDestination
import com.stockflip.NotificationNavigationSecurity
import com.stockflip.R
import com.stockflip.UpdateCheckResult
import com.stockflip.UpdateReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodisk bakgrundskontroll (var 24:e timme, se AppUpdateScheduler) för om en nyare
 * StockFlip-release finns på GitHub. Ett misslyckat kontrollanrop är inte värt
 * WorkManager-retry för en dygnsvis bakgrundsfunktion -- AppUpdateChecker sväljer redan
 * det underliggande nätverksfelet, så doWork() returnerar alltid success.
 */
class AppUpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        AppUpdateSettings.init(applicationContext)
        val result = AppUpdateChecker().checkForUpdateRespectingSkip()
        AppUpdateSettings.setLastCheckTimestampMillis(System.currentTimeMillis())

        if (result is UpdateCheckResult.UpdateAvailable) {
            showUpdateAvailableNotification(result.release)
        }

        Result.success()
    }

    private fun showUpdateAvailableNotification(release: UpdateReleaseInfo) {
        val title = "Uppdatering tillgänglig"
        val message = "StockFlip v${release.versionName} kan hämtas."

        val destination = NotificationDestination.AppUpdate(release.versionName)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATE_VERSION, release.versionName)
            putExtra(MainActivity.EXTRA_TRIGGER_TITLE, title)
            putExtra(MainActivity.EXTRA_TRIGGER_MESSAGE, message)
            putExtra(MainActivity.EXTRA_NOTIFICATION_TOKEN, NotificationNavigationSecurity.issueToken(destination))
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(applicationContext, AppUpdateNotifier.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Öppna appen för att visa uppdateringen")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notification = NotificationCompat.Builder(applicationContext, AppUpdateNotifier.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Deterministiskt notis-ID så en ny kontroll ersätter -- inte staplar -- en
        // tidigare "uppdatering tillgänglig"-notis.
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Sent app update available notification for v${release.versionName}")
    }

    companion object {
        private const val TAG = "AppUpdateCheckWorker"
        private const val NOTIFICATION_ID = 9001
    }
}
