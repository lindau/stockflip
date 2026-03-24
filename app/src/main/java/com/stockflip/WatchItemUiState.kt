package com.stockflip

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
