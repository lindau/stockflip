package com.stockflip

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity för att lagra historisk data för nyckeltal.
 * 
 * Varje rad representerar ett nyckeltal-värde vid en specifik tidpunkt.
 */
@Entity(tableName = "metric_history")
data class MetricHistoryEntity(
    @PrimaryKey val id: String, // Format: "SYMBOL_METRICTYPE_TIMESTAMP"
    val symbol: String,
    val metricType: String, // WatchType.MetricType.name
    val date: Long, // Timestamp i millisekunder
    val value: Double
) {
    companion object {
        /**
         * Skapar ett ID för en MetricHistoryEntity.
         */
        fun createId(symbol: String, metricType: WatchType.MetricType, date: Long): String {
            return "${symbol}_${metricType.name}_$date"
        }
    }
}

