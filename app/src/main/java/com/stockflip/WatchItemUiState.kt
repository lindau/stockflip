package com.stockflip

import kotlin.math.abs

/**
 * Transient live data for a WatchItem — never persisted to the database.
 * Replaces the @Ignore fields that were previously on WatchItem.
 */
data class LiveWatchData(
    val currentPrice1: Double = 0.0,
    val currentPrice2: Double = 0.0,
    val currentPrice: Double = 0.0,
    val currentMetricValue: Double = 0.0,
    val metricValueAtCreation: Double = 0.0,
    val currentATH: Double = 0.0,
    val currentDropPercentage: Double = 0.0,
    val currentDropAbsolute: Double = 0.0,
    val currentDailyChangePercent: Double? = null,
    val lastUpdatedAt: Long = 0L,
    val updateFailed: Boolean = false
)

/**
 * Combines a persisted WatchItem entity with its transient live market data.
 * Data class equality covers both item and live — no manual DiffCallback comparisons needed.
 */
data class WatchItemUiState(
    val item: WatchItem,
    val live: LiveWatchData = LiveWatchData()
)

fun WatchItemUiState.isTriggeredForDisplay(): Boolean {
    return item.isTriggered || hasLiveTriggerCondition()
}

fun WatchItemUiState.isTriggeredTodayForDisplay(today: String): Boolean {
    return hasLiveTriggerCondition() || (item.isTriggered && item.lastTriggeredDate == today)
}

private fun WatchItemUiState.hasLiveTriggerCondition(): Boolean {
    return when (val watchType = item.watchType) {
        is WatchType.PricePair -> {
            if (live.currentPrice1 <= 0.0 || live.currentPrice2 <= 0.0) return false
            val diff = abs(live.currentPrice1 - live.currentPrice2)
            (watchType.notifyWhenEqual && diff < PRICE_EQUALITY_THRESHOLD) ||
                (watchType.priceDifference > 0.0 && diff <= watchType.priceDifference)
        }

        is WatchType.PriceTarget -> {
            if (live.currentPrice <= 0.0 || watchType.targetPrice <= 0.0) return false
            when (watchType.direction) {
                WatchType.PriceDirection.ABOVE -> live.currentPrice >= watchType.targetPrice
                WatchType.PriceDirection.BELOW -> live.currentPrice <= watchType.targetPrice
            }
        }

        is WatchType.KeyMetrics -> {
            if (live.currentMetricValue <= 0.0 || watchType.targetValue <= 0.0) return false
            when (watchType.direction) {
                WatchType.PriceDirection.ABOVE -> live.currentMetricValue >= watchType.targetValue
                WatchType.PriceDirection.BELOW -> live.currentMetricValue <= watchType.targetValue
            }
        }

        is WatchType.ATHBased -> {
            if (watchType.dropValue <= 0.0) return false
            val currentValue = when (watchType.dropType) {
                WatchType.DropType.PERCENTAGE -> live.currentDropPercentage
                WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute
            }
            currentValue >= watchType.dropValue
        }

        is WatchType.DailyMove -> {
            val currentChange = live.currentDailyChangePercent ?: return false
            val currentMove = when (watchType.direction) {
                WatchType.DailyMoveDirection.UP -> currentChange.coerceAtLeast(0.0)
                WatchType.DailyMoveDirection.DOWN -> (-currentChange).coerceAtLeast(0.0)
                WatchType.DailyMoveDirection.BOTH -> abs(currentChange)
            }
            watchType.percentThreshold > 0.0 && currentMove >= watchType.percentThreshold
        }

        is WatchType.PriceRange -> {
            live.currentPrice > 0.0 && live.currentPrice in watchType.minPrice..watchType.maxPrice
        }

        is WatchType.Combined -> false
    }
}

private const val PRICE_EQUALITY_THRESHOLD = 0.01
