package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Äger notiskanalen för app-uppdateringar -- egen kanal, skild från
 * StockPriceUpdater.CHANNEL_ID, så användaren kan tysta uppdateringsnotiser utan att
 * påverka prislarm.
 */
object AppUpdateNotifier {
    const val CHANNEL_ID = "app_updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Appuppdateringar",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Meddelar när en ny version av StockFlip finns tillgänglig"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
