package com.stockflip

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object för MetricHistoryEntity.
 * 
 * Hanterar CRUD-operationer för historisk nyckeltal-data.
 */
@Dao
interface MetricHistoryDao {
    /**
     * Infogar eller uppdaterar en historisk datapunkt.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetricHistory(history: MetricHistoryEntity)

    /**
     * Infogar eller uppdaterar flera historiska datapunkter.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetricHistoryList(historyList: List<MetricHistoryEntity>)

    /**
     * Hämtar historisk data för en specifik aktie och nyckeltal-typ inom ett tidsintervall.
     */
    @Query("""
        SELECT * FROM metric_history 
        WHERE symbol = :symbol 
        AND metricType = :metricType 
        AND date >= :startDate 
        AND date <= :endDate
        ORDER BY date ASC
    """)
    suspend fun getMetricHistory(
        symbol: String,
        metricType: String,
        startDate: Long,
        endDate: Long
    ): List<MetricHistoryEntity>

    /**
     * Hämtar senaste N dagars historik för en specifik aktie och nyckeltal-typ.
     */
    @Query("""
        SELECT * FROM metric_history 
        WHERE symbol = :symbol 
        AND metricType = :metricType 
        AND date >= :startDate
        ORDER BY date ASC
    """)
    suspend fun getLatestHistory(
        symbol: String,
        metricType: String,
        startDate: Long
    ): List<MetricHistoryEntity>

    /**
     * Hämtar alla historiska datapunkter för en specifik aktie och nyckeltal-typ.
     */
    @Query("""
        SELECT * FROM metric_history 
        WHERE symbol = :symbol 
        AND metricType = :metricType
        ORDER BY date ASC
    """)
    suspend fun getAllHistoryForMetric(
        symbol: String,
        metricType: String
    ): List<MetricHistoryEntity>

    @Query("SELECT * FROM metric_history")
    suspend fun getAllEntries(): List<MetricHistoryEntity>

    /**
     * Tar bort gammal historik äldre än angivet datum.
     * Används för cleanup.
     */
    @Query("""
        DELETE FROM metric_history 
        WHERE date < :olderThan
    """)
    suspend fun deleteOldHistory(olderThan: Long)

    /**
     * Tar bort all historik för en specifik aktie och nyckeltal-typ.
     */
    @Query("""
        DELETE FROM metric_history 
        WHERE symbol = :symbol 
        AND metricType = :metricType
    """)
    suspend fun deleteHistoryForMetric(symbol: String, metricType: String)

    /**
     * Hämtar senaste uppdaterade datumet för en specifik aktie och nyckeltal-typ.
     */
    @Query("""
        SELECT MAX(date) FROM metric_history 
        WHERE symbol = :symbol 
        AND metricType = :metricType
    """)
    suspend fun getLastUpdateDate(symbol: String, metricType: String): Long?
}
