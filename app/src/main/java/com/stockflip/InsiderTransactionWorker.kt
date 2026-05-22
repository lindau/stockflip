package com.stockflip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stockflip.repository.TriggerHistoryRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InsiderTransactionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = StockPairDatabase.getDatabase(applicationContext)
        val watchItemDao = database.watchItemDao()
        val transactionDao = database.insiderTransactionDao()
        val triggerHistoryRepository = TriggerHistoryRepository(database.triggerHistoryDao())
        val secService = SecInsiderTransactionService()
        val fiService = FiInsiderTransactionService()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val insiderWatchItems = watchItemDao.getAllWatchItems()
            .filter { item ->
                item.isActive &&
                    item.canTrigger(today) &&
                    item.watchType is WatchType.InsiderBuy &&
                    item.ticker != null
            }

        if (insiderWatchItems.isEmpty()) {
            Log.d(TAG, "No active insider purchase watches")
            return Result.success()
        }

        insiderWatchItems.forEachIndexed { index, item ->
            try {
                val ticker = item.ticker ?: return@forEachIndexed
                val watchType = item.watchType as WatchType.InsiderBuy
                val purchases = if (isSwedishTicker(ticker)) {
                    fiService.getRecentInsiderPurchases(
                        symbol = ticker,
                        issuerName = item.companyName,
                        minPublishedAtMillis = watchType.createdAtMillis
                    )
                } else {
                    secService.getRecentInsiderPurchases(
                        symbol = ticker,
                        minAcceptedAtMillis = watchType.createdAtMillis
                    )
                }
                if (purchases.isEmpty()) return@forEachIndexed

                val existingIds = transactionDao.getExistingIds(purchases.map { it.id }).toSet()
                val newPurchases = purchases.filterNot { it.id in existingIds }
                transactionDao.insertAll(purchases.map { it.toEntity() })
                if (newPurchases.isEmpty()) return@forEachIndexed

                showInsiderPurchaseNotification(item, newPurchases)
                watchItemDao.update(item.markAsTriggered(today))
                triggerHistoryRepository.record(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check insider purchases: ${e.message}")
            }

            if (index < insiderWatchItems.lastIndex) {
                delay(SEC_REQUEST_SPACING_MS)
            }
        }

        return Result.success()
    }

    private fun showInsiderPurchaseNotification(
        item: WatchItem,
        purchases: List<InsiderPurchase>
    ) {
        val ticker = item.ticker ?: return
        val companyName = item.companyName ?: ticker
        val totalValue = purchases.mapNotNull { it.estimatedValue }.sum()
        val currency = purchases.firstOrNull { it.cik == FI_SOURCE }?.let { "SEK" } ?: "USD"
        val title = if (purchases.size == 1) {
            "Insiderköp i $companyName"
        } else {
            "${purchases.size} insiderköp i $companyName"
        }
        val leadPurchase = purchases.maxByOrNull { it.estimatedValue ?: 0.0 } ?: purchases.first()
        val valueText = totalValue.takeIf { it > 0.0 }?.let {
            " Totalt cirka ${CurrencyHelper.formatPrice(it, currency)}."
        }.orEmpty()
        val message = "${leadPurchase.reportingOwner} rapporterade köp av ${leadPurchase.securityTitle ?: ticker}.$valueText"

        val notificationToken = NotificationNavigationSecurity.issueToken()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TICKER, ticker)
            putExtra(MainActivity.EXTRA_OPEN_COMPANY, item.companyName)
            putExtra(MainActivity.EXTRA_OPEN_WATCH_ID, item.id)
            putExtra(MainActivity.EXTRA_OPEN_INSIDER_TRANSACTION_ID, leadPurchase.id)
            putExtra(MainActivity.EXTRA_TRIGGER_TITLE, title)
            putExtra(MainActivity.EXTRA_TRIGGER_MESSAGE, message)
            putExtra(MainActivity.EXTRA_NOTIFICATION_TOKEN, notificationToken)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(applicationContext, StockPriceUpdater.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Öppna appen för att visa bevakningsdetaljer")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notification = NotificationCompat.Builder(applicationContext, StockPriceUpdater.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Sent insider purchase notification")
    }

    companion object {
        private const val TAG = "InsiderTransactionWorker"
        private const val FI_SOURCE = "FI"
        private const val SEC_REQUEST_SPACING_MS = 150L

        private fun isSwedishTicker(ticker: String): Boolean {
            return ticker.endsWith(".ST", ignoreCase = true) ||
                ticker.endsWith(".STO", ignoreCase = true)
        }
    }
}
