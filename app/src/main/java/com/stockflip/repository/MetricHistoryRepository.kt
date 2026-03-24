package com.stockflip.repository

import android.util.Log
import com.stockflip.MetricHistoryDao
import com.stockflip.MetricHistoryEntity
import com.stockflip.MetricHistorySummary
import com.stockflip.PeriodSummary
import com.stockflip.WatchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

/**
 * Repository för att hantera historisk nyckeltal-data.
 * 
 * Hanterar hämtning, beräkning och lagring av historisk data för nyckeltal.
 */
class MetricHistoryRepository(
    private val dao: MetricHistoryDao
) {
    private val TAG = "MetricHistoryRepository"
    
    companion object {
        private const val ONE_YEAR_DAYS = 365
        private const val THREE_YEAR_DAYS = 3 * 365
        private const val FIVE_YEAR_DAYS = 5 * 365
        private val MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1)
    }

    /**
     * Hämtar MetricHistorySummary för en specifik aktie och nyckeltal-typ.
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @return MetricHistorySummary med 1/3/5 års sammanfattningar, eller null om ingen data finns
     */
    suspend fun getMetricHistorySummary(
        symbol: String,
        metricType: WatchType.MetricType
    ): MetricHistorySummary? {
        return try {
            val now = System.currentTimeMillis()
            val oneYearStart = now - (ONE_YEAR_DAYS * MILLIS_PER_DAY)
            val threeYearStart = now - (THREE_YEAR_DAYS * MILLIS_PER_DAY)
            val fiveYearStart = now - (FIVE_YEAR_DAYS * MILLIS_PER_DAY)

            val allHistory = dao.getMetricHistory(symbol, metricType.name, fiveYearStart, now)

            if (allHistory.isEmpty()) {
                Log.d(TAG, "No history found for $symbol ${metricType.name}")
                return null
            }

            val oneYear = calculatePeriodSummary(allHistory, oneYearStart, now)
            val threeYear = calculatePeriodSummary(allHistory, threeYearStart, now)
            val fiveYear = calculatePeriodSummary(allHistory, fiveYearStart, now)

            MetricHistorySummary(
                metricType = metricType,
                symbol = symbol,
                oneYear = oneYear,
                threeYear = threeYear,
                fiveYear = fiveYear,
                lastUpdated = allHistory.maxOfOrNull { it.date } ?: now
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting metric history summary: ${e.message}", e)
            null
        }
    }

    /**
     * Hämtar MetricHistorySummary som Flow.
     */
    fun getMetricHistorySummaryFlow(
        symbol: String,
        metricType: WatchType.MetricType
    ): Flow<MetricHistorySummary?> = flow {
        emit(getMetricHistorySummary(symbol, metricType))
    }

    /**
     * Beräknar PeriodSummary för en specifik tidsperiod.
     * 
     * @param history All historisk data
     * @param startDate Startdatum för perioden (timestamp)
     * @param endDate Slutdatum för perioden (timestamp)
     * @return PeriodSummary med min, max, snitt och median
     */
    private fun calculatePeriodSummary(
        history: List<MetricHistoryEntity>,
        startDate: Long,
        endDate: Long
    ): PeriodSummary {
        val filtered = history.filter { 
            it.date >= startDate && it.date <= endDate
        }
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val startDateStr = dateFormat.format(java.util.Date(startDate))
        val endDateStr = dateFormat.format(java.util.Date(endDate))
        
        // Log first and last dates in history for debugging
        val firstHistoryDate = history.minOfOrNull { it.date }?.let { dateFormat.format(java.util.Date(it)) } ?: "N/A"
        val lastHistoryDate = history.maxOfOrNull { it.date }?.let { dateFormat.format(java.util.Date(it)) } ?: "N/A"
        val firstFilteredDate = filtered.minOfOrNull { it.date }?.let { dateFormat.format(java.util.Date(it)) } ?: "N/A"
        val lastFilteredDate = filtered.maxOfOrNull { it.date }?.let { dateFormat.format(java.util.Date(it)) } ?: "N/A"
        
        Log.d(TAG, "Calculating period summary:")
        Log.d(TAG, "  Period: $startDateStr to $endDateStr")
        Log.d(TAG, "  Total history: ${history.size} points (from $firstHistoryDate to $lastHistoryDate)")
        Log.d(TAG, "  Filtered: ${filtered.size} points (from $firstFilteredDate to $lastFilteredDate)")
        
        if (filtered.isEmpty()) {
            Log.d(TAG, "No data in period, returning empty summary")
            return PeriodSummary(0.0, 0.0, 0.0, null)
        }
        
        val values = filtered.map { it.value }
        val sortedValues = values.sorted()
        val uniqueValues = values.distinct()
        
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val average = values.average()
        
        val median = if (sortedValues.isNotEmpty()) {
            if (sortedValues.size % 2 == 0) {
                (sortedValues[sortedValues.size / 2 - 1] + sortedValues[sortedValues.size / 2]) / 2.0
            } else {
                sortedValues[sortedValues.size / 2]
            }
        } else {
            null
        }
        
        Log.d(TAG, "Period summary results:")
        Log.d(TAG, "  min=$min, max=$max, average=$average, median=$median")
        Log.d(TAG, "  dataPoints=${values.size}, uniqueValues=${uniqueValues.size}")
        if (uniqueValues.size == 1) {
            Log.w(TAG, "  WARNING: All values in period are identical (${uniqueValues.first()})")
        }
        
        return PeriodSummary(
            min = min,
            max = max,
            average = average,
            median = median
        )
    }

    /**
     * Sparar en historisk datapunkt.
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @param value Värdet
     * @param date Datum (timestamp), default är nuvarande tid
     */
    suspend fun saveMetricHistory(
        symbol: String,
        metricType: WatchType.MetricType,
        value: Double,
        date: Long = System.currentTimeMillis()
    ) {
        try {
            val entity = MetricHistoryEntity(
                id = MetricHistoryEntity.createId(symbol, metricType, date),
                symbol = symbol,
                metricType = metricType.name,
                date = date,
                value = value
            )
            dao.insertMetricHistory(entity)
            Log.d(TAG, "Saved metric history: $symbol ${metricType.name} = $value at $date")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metric history: ${e.message}", e)
        }
    }

    /**
     * Sparar flera historiska datapunkter.
     */
    suspend fun saveMetricHistoryList(
        symbol: String,
        metricType: WatchType.MetricType,
        historyData: List<Pair<Long, Double>> // List of (date, value) pairs
    ) {
        try {
            val entities = historyData.map { (date, value) ->
                MetricHistoryEntity(
                    id = MetricHistoryEntity.createId(symbol, metricType, date),
                    symbol = symbol,
                    metricType = metricType.name,
                    date = date,
                    value = value
                )
            }
            dao.insertMetricHistoryList(entities)
            Log.d(TAG, "Saved ${entities.size} metric history entries for $symbol ${metricType.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metric history list: ${e.message}", e)
        }
    }

    /**
     * Kontrollerar om historik behöver uppdateras.
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @param maxAgeHours Maximal ålder i timmar innan uppdatering behövs
     * @return true om historik är äldre än maxAgeHours eller saknas
     */
    suspend fun needsUpdate(
        symbol: String,
        metricType: WatchType.MetricType,
        maxAgeHours: Long = 24
    ): Boolean {
        val lastUpdate = dao.getLastUpdateDate(symbol, metricType.name)
        if (lastUpdate == null) {
            return true // Ingen data finns
        }
        
        val maxAgeMillis = TimeUnit.HOURS.toMillis(maxAgeHours)
        val now = System.currentTimeMillis()
        return (now - lastUpdate) > maxAgeMillis
    }

    /**
     * Kontrollerar om historiken har tillräckligt med data (t.ex. minst X dagar).
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal
     * @param minDays Minsta antal dagar med data som krävs
     * @return true om historiken har tillräckligt med data, false annars
     */
    suspend fun hasEnoughHistory(
        symbol: String,
        metricType: WatchType.MetricType,
        minDays: Int = 365
    ): Boolean {
        val fiveYearsAgo = System.currentTimeMillis() - (FIVE_YEAR_DAYS * MILLIS_PER_DAY)
        val allHistory = dao.getMetricHistory(symbol, metricType.name, fiveYearsAgo, System.currentTimeMillis())
        if (allHistory.isEmpty()) {
            return false
        }
        
        val firstDate = allHistory.minOf { it.date }
        val lastDate = allHistory.maxOf { it.date }
        val daysWithData = ((lastDate - firstDate) / (1000 * 60 * 60 * 24)).toInt() + 1
        
        Log.d(TAG, "Checking history sufficiency for $symbol ${metricType.name}: $daysWithData days (min: $minDays)")
        return daysWithData >= minDays
    }

    /**
     * Tar bort gammal historik äldre än angivet antal år.
     */
    suspend fun cleanupOldHistory(olderThanYears: Int = 5) {
        try {
            val cutoffDate = System.currentTimeMillis() - (olderThanYears * 365 * MILLIS_PER_DAY)
            dao.deleteOldHistory(cutoffDate)
            Log.d(TAG, "Cleaned up history older than $olderThanYears years")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old history: ${e.message}", e)
        }
    }

    /**
     * Tar bort all historik för en specifik aktie och nyckeltal-typ.
     */
    suspend fun deleteHistoryForMetric(
        symbol: String,
        metricType: WatchType.MetricType
    ) {
        try {
            dao.deleteHistoryForMetric(symbol, metricType.name)
            Log.d(TAG, "Deleted history for $symbol ${metricType.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting history: ${e.message}", e)
        }
    }

    /**
     * Exponerar DAO för direkt åtkomst när det behövs.
     */
    val metricHistoryDao: MetricHistoryDao
        get() = dao
}

