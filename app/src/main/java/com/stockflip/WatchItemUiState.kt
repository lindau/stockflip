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
    if (item.hasPendingNextTradingDayGuard()) return false
    return item.isTriggered || hasLiveTriggerCondition()
}

fun WatchItemUiState.isTriggeredTodayForDisplay(today: String): Boolean {
    if (item.hasPendingNextTradingDayGuard(today)) return false
    return hasLiveTriggerCondition() || (item.isTriggered && item.lastTriggeredDate == today)
}

internal fun WatchItemUiState.hasLiveTriggerCondition(): Boolean {
    return when (val watchType = item.watchType) {
        is WatchType.PricePair -> {
            if (live.currentPrice1 <= 0.0 || live.currentPrice2 <= 0.0) return false
            PairTriggerEvaluator.evaluate(
                priceA = live.currentPrice1,
                priceB = live.currentPrice2,
                spreadTarget = watchType.priceDifference,
                notifyWhenEqual = watchType.notifyWhenEqual
            ) != null
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

        is WatchType.InsiderBuy -> false
        is WatchType.Combined -> false
    }
}

/**
 * Hur nära bevakningens villkor är att utlösas, som ett tal mellan 0.0 (utlöst nu)
 * och 1.0 (långt kvar), eller null om det inte går att beräkna för denna typ.
 */
internal fun WatchItemUiState.triggerProximity(): Double? {
    return when (val watchType = item.watchType) {
        is WatchType.PriceTarget -> {
            if (live.currentPrice <= 0.0 || watchType.targetPrice <= 0.0) null
            else when (watchType.direction) {
                WatchType.PriceDirection.ABOVE ->
                    (watchType.targetPrice - live.currentPrice).coerceAtLeast(0.0) / watchType.targetPrice
                WatchType.PriceDirection.BELOW ->
                    (live.currentPrice - watchType.targetPrice).coerceAtLeast(0.0) / watchType.targetPrice
            }
        }

        is WatchType.KeyMetrics -> {
            if (live.currentMetricValue <= 0.0 || watchType.targetValue <= 0.0) null
            else when (watchType.direction) {
                WatchType.PriceDirection.ABOVE ->
                    (watchType.targetValue - live.currentMetricValue).coerceAtLeast(0.0) / watchType.targetValue
                WatchType.PriceDirection.BELOW ->
                    (live.currentMetricValue - watchType.targetValue).coerceAtLeast(0.0) / watchType.targetValue
            }
        }

        is WatchType.ATHBased -> {
            val currentValue = when (watchType.dropType) {
                WatchType.DropType.PERCENTAGE -> live.currentDropPercentage
                WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute
            }
            if (currentValue <= 0.0 || watchType.dropValue <= 0.0) null
            else (watchType.dropValue - currentValue).coerceAtLeast(0.0) / watchType.dropValue
        }

        is WatchType.DailyMove -> {
            val currentChange = live.currentDailyChangePercent ?: return null
            val currentMove = when (watchType.direction) {
                WatchType.DailyMoveDirection.UP -> currentChange.coerceAtLeast(0.0)
                WatchType.DailyMoveDirection.DOWN -> (-currentChange).coerceAtLeast(0.0)
                WatchType.DailyMoveDirection.BOTH -> abs(currentChange)
            }
            if (watchType.percentThreshold <= 0.0) null
            else (watchType.percentThreshold - currentMove).coerceAtLeast(0.0) / watchType.percentThreshold
        }

        is WatchType.PriceRange -> {
            if (live.currentPrice <= 0.0) null
            else when {
                live.currentPrice in watchType.minPrice..watchType.maxPrice -> 0.0
                live.currentPrice < watchType.minPrice -> abs(live.currentPrice - watchType.minPrice) / watchType.minPrice
                else -> abs(live.currentPrice - watchType.maxPrice) / watchType.maxPrice
            }
        }

        is WatchType.PricePair -> null
        is WatchType.InsiderBuy -> null
        is WatchType.Combined -> null
    }
}
