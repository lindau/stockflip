package com.stockflip

import androidx.room.TypeConverter

/**
 * Type converters for Room database to handle WatchType serialization.
 */
class WatchTypeConverter {
    @TypeConverter
    fun fromWatchType(watchType: WatchType): String {
        return when (watchType) {
            is WatchType.PricePair -> "PRICE_PAIR|${watchType.priceDifference}|${watchType.notifyWhenEqual}"
            is WatchType.PriceTarget -> "PRICE_TARGET|${watchType.targetPrice}|${watchType.direction.name}"
            is WatchType.KeyMetrics -> "KEY_METRICS|${watchType.metricType.name}|${watchType.targetValue}|${watchType.direction.name}"
            is WatchType.ATHDrop -> "ATH_DROP|${watchType.dropPercentage}"
            is WatchType.DailyHighDrop -> "DAILY_HIGH_DROP|${watchType.dropPercentage}"
        }
    }

    @TypeConverter
    fun toWatchType(value: String): WatchType {
        val parts = value.split("|")
        return when (parts[0]) {
            "PRICE_PAIR" -> WatchType.PricePair(
                priceDifference = parts[1].toDouble(),
                notifyWhenEqual = parts[2].toBoolean()
            )
            "PRICE_TARGET" -> WatchType.PriceTarget(
                targetPrice = parts[1].toDouble(),
                direction = WatchType.PriceDirection.valueOf(parts[2])
            )
            "KEY_METRICS" -> WatchType.KeyMetrics(
                metricType = WatchType.MetricType.valueOf(parts[1]),
                targetValue = parts[2].toDouble(),
                direction = WatchType.PriceDirection.valueOf(parts[3])
            )
            "ATH_DROP" -> WatchType.ATHDrop(
                dropPercentage = parts[1].toDouble()
            )
            "DAILY_HIGH_DROP" -> WatchType.DailyHighDrop(
                dropPercentage = parts[1].toDouble()
            )
            else -> throw IllegalArgumentException("Unknown watch type: ${parts[0]}")
        }
    }
}

