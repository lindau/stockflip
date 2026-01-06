package com.stockflip.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import com.stockflip.MetricHistoryService
import com.stockflip.StockPairDatabase
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.YahooFinanceService
import com.stockflip.repository.MetricHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker för att uppdatera historisk nyckeltal-data.
 * 
 * Kör nattligt (t.ex. 02:00) och uppdaterar historik för alla aktier med KeyMetrics-bevakningar.
 * Begränsad till WiFi + laddning för batterioptimering.
 */
class MetricHistoryUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting metric history update")
            
            val database = StockPairDatabase.getDatabase(applicationContext)
            val watchItemDao = database.watchItemDao()
            val metricHistoryRepository = MetricHistoryRepository(database.metricHistoryDao())
            val metricHistoryService = MetricHistoryService(metricHistoryRepository)
            
            // Hämta alla KeyMetrics-bevakningar
            val allWatchItems = watchItemDao.getAllWatchItems()
            val keyMetricsItems = allWatchItems.filter { it.watchType is WatchType.KeyMetrics }
            
            if (keyMetricsItems.isEmpty()) {
                Log.d(TAG, "No KeyMetrics items found, skipping history update")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${keyMetricsItems.size} KeyMetrics items to update")
            
            var updatedCount = 0
            var errorCount = 0
            
            // Uppdatera historik för varje KeyMetrics-bevakning
            keyMetricsItems.forEach { item ->
                try {
                    val keyMetrics = item.watchType as WatchType.KeyMetrics
                    val ticker = item.ticker ?: return@forEach
                    
                    Log.d(TAG, "Updating history for $ticker ${keyMetrics.metricType.name}")
                    
                    // Hämta nuvarande värde
                    val currentValue = YahooFinanceService.getKeyMetric(ticker, keyMetrics.metricType)
                    
                    // Uppdatera historik (endast om cache är gammal)
                    metricHistoryService.fetchAndSaveHistory(ticker, keyMetrics.metricType, currentValue)
                    
                    updatedCount++
                    Log.d(TAG, "Successfully updated history for $ticker ${keyMetrics.metricType.name}")
                    
                    // Lägg till liten delay för att undvika rate limiting
                    kotlinx.coroutines.delay(1000) // 1 sekund mellan requests
                    
                } catch (e: Exception) {
                    errorCount++
                    Log.e(TAG, "Error updating history for ${item.ticker}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Metric history update completed: $updatedCount updated, $errorCount errors")
            
            // Rensa gammal historik (äldre än 5 år)
            try {
                metricHistoryService.cleanupOldHistory(olderThanYears = 5)
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old history: ${e.message}", e)
            }
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Metric history update worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MetricHistoryUpdateWorker"
        const val WORK_NAME = "MetricHistoryUpdatePeriodic"
        
        /**
         * Schemalägger nattlig uppdatering av historik.
         * Kör en gång per dag vid ungefär 02:00.
         */
        fun scheduleNightlyUpdate(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            // Constraints: WiFi + laddning för batterioptimering
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi
                .setRequiresCharging(true) // Endast när laddning
                .build()
            
            // Periodic work: en gång per dag (24 timmar)
            val periodicWork = PeriodicWorkRequestBuilder<MetricHistoryUpdateWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS // Flex period
            )
                .setConstraints(constraints)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
            
            Log.d(TAG, "Scheduled nightly metric history update")
        }
    }
}

