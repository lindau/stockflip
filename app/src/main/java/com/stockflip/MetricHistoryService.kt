package com.stockflip

import android.util.Log
import com.stockflip.repository.MetricHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Service för att hantera historisk nyckeltal-data.
 * 
 * Koordinerar hämtning från API och lagring i Room via MetricHistoryRepository.
 * 
 * Strategi:
 * - För nuvarande värden: Hämta från Finnhub/Yahoo Finance och spara dagligen
 * - För historisk data: Försök hämta från Finnhub financials, annars bygg historik över tid
 */
class MetricHistoryService(
    private val metricHistoryRepository: MetricHistoryRepository
) {
    private val TAG = "MetricHistoryService"

    /**
     * Hämtar och sparar historisk data för ett nyckeltal.
     * 
     * Försöker först hämta historik från API, annars bygger historik genom att
     * spara nuvarande värden regelbundet.
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @param currentValue Nuvarande värde (sparas om historik saknas)
     */
    suspend fun fetchAndSaveHistory(
        symbol: String,
        metricType: WatchType.MetricType,
        currentValue: Double?
    ) = withContext(Dispatchers.IO) {
        try {
            // Kontrollera om historik behöver uppdateras
            val needsUpdate = metricHistoryRepository.needsUpdate(symbol, metricType, maxAgeHours = 24)
            
            if (!needsUpdate) {
                Log.d(TAG, "History for $symbol ${metricType.name} is up to date")
                return@withContext
            }

            Log.d(TAG, "Fetching historical data for $symbol ${metricType.name}")

            // Försök hämta historik från Finnhub
            val historyData = FinnhubService.getMetricHistory(symbol, metricType, years = 5)
            
            if (historyData != null && historyData.isNotEmpty()) {
                // Spara historisk data från API
                val historyPairs = historyData.map { it.date to it.value }
                metricHistoryRepository.saveMetricHistoryList(symbol, metricType, historyPairs)
                Log.d(TAG, "Saved ${historyData.size} historical data points from API for $symbol ${metricType.name}")
            } else {
                // Ingen historik från API, spara nuvarande värde om det finns
                if (currentValue != null && currentValue > 0) {
                    metricHistoryRepository.saveMetricHistory(symbol, metricType, currentValue)
                    Log.d(TAG, "No API history available, saved current value for $symbol ${metricType.name}: $currentValue")
                } else {
                    Log.w(TAG, "No historical data available and no current value to save for $symbol ${metricType.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching and saving history: ${e.message}", e)
        }
    }

    /**
     * Hämtar MetricHistorySummary för en aktie och nyckeltal-typ.
     * 
     * Om historik saknas eller är gammal, försöker hämta och uppdatera.
     */
    suspend fun getOrFetchHistorySummary(
        symbol: String,
        metricType: WatchType.MetricType,
        currentValue: Double? = null
    ): MetricHistorySummary? = withContext(Dispatchers.IO) {
        try {
            // Försök hämta befintlig sammanfattning
            var summary = metricHistoryRepository.getMetricHistorySummary(symbol, metricType)
            
            // Om ingen sammanfattning finns eller är gammal, försök uppdatera
            if (summary == null || metricHistoryRepository.needsUpdate(symbol, metricType, maxAgeHours = 24)) {
                Log.d(TAG, "History summary missing or outdated, fetching new data")
                fetchAndSaveHistory(symbol, metricType, currentValue)
                summary = metricHistoryRepository.getMetricHistorySummary(symbol, metricType)
            }
            
            summary
        } catch (e: Exception) {
            Log.e(TAG, "Error getting history summary: ${e.message}", e)
            null
        }
    }

    /**
     * Sparar nuvarande värde som historisk datapunkt.
     * 
     * Används för att bygga historik över tid när API inte ger historisk data.
     * Sparar endast om vi inte redan har ett värde för idag (undviker dubbletter).
     */
    suspend fun saveCurrentValueAsHistory(
        symbol: String,
        metricType: WatchType.MetricType,
        value: Double
    ) = withContext(Dispatchers.IO) {
        try {
            val today = System.currentTimeMillis()
            val todayStart = today - (today % TimeUnit.DAYS.toMillis(1))
            val todayEnd = todayStart + TimeUnit.DAYS.toMillis(1) - 1
            
            // Kontrollera om vi redan har sparat ett värde idag
            val existingToday = metricHistoryRepository.metricHistoryDao.getMetricHistory(
                symbol,
                metricType.name,
                todayStart,
                todayEnd
            )
            
            if (existingToday.isEmpty()) {
                // Inget värde för idag, spara det
                metricHistoryRepository.saveMetricHistory(symbol, metricType, value, today)
                Log.d(TAG, "Saved current value as history for $symbol ${metricType.name}: $value")
            } else {
                Log.d(TAG, "Value for today already exists for $symbol ${metricType.name}, skipping")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving current value as history: ${e.message}", e)
        }
    }

    /**
     * Rensar gammal historik äldre än angivet antal år.
     */
    suspend fun cleanupOldHistory(olderThanYears: Int = 5) = withContext(Dispatchers.IO) {
        try {
            metricHistoryRepository.cleanupOldHistory(olderThanYears)
            Log.d(TAG, "Cleaned up history older than $olderThanYears years")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old history: ${e.message}", e)
        }
    }
}

