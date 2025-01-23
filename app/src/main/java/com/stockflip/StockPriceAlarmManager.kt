package com.stockflip

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class StockPriceAlarmManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun scheduleStockPriceCheck(intervalMinutes: Long = 15) {
        val intent = Intent(context, PriceUpdateReceiver::class.java).apply {
            action = PriceUpdateReceiver.ACTION_PRICE_UPDATE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes)
        val startTime = System.currentTimeMillis()

        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                startTime,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled stock price check every $intervalMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule stock price check: ${e.message}")
        }
    }

    fun showNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stock Price Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for stock price alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "StockPriceAlarmManager"
        private const val CHANNEL_ID = "stock_price_alerts"
        private const val NOTIFICATION_ID = 1
    }
} 