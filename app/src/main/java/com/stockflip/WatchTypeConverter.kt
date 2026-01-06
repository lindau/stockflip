package com.stockflip

import android.util.Base64
import androidx.room.TypeConverter
import java.nio.charset.StandardCharsets

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
            is WatchType.ATHBased -> "ATH_BASED|${watchType.dropType.name}|${watchType.dropValue}"
            is WatchType.PriceRange -> "PRICE_RANGE|${watchType.minPrice}|${watchType.maxPrice}"
            is WatchType.DailyMove -> "DAILY_MOVE|${watchType.percentThreshold}|${watchType.direction.name}"
            is WatchType.Combined -> {
                // Använd Base64 för att undvika problem med "|" i JSON
                val json = AlertExpressionConverter.toJson(watchType.expression)
                val encoded = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                "COMBINED|$encoded"
            }
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
            "ATH_BASED" -> WatchType.ATHBased(
                dropType = WatchType.DropType.valueOf(parts[1]),
                dropValue = parts[2].toDouble()
            )
            "PRICE_RANGE" -> WatchType.PriceRange(
                minPrice = parts[1].toDouble(),
                maxPrice = parts[2].toDouble()
            )
            "DAILY_MOVE" -> WatchType.DailyMove(
                percentThreshold = parts[1].toDouble(),
                direction = WatchType.DailyMoveDirection.valueOf(parts[2])
            )
            "COMBINED" -> {
                // AlertExpression är serialiserad som Base64-kodad JSON
                val encoded = parts[1]
                val jsonString = String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
                WatchType.Combined(
                    expression = AlertExpressionConverter.fromJson(jsonString)
                )
            }
            else -> throw IllegalArgumentException("Unknown watch type: ${parts[0]}")
        }
    }
}

