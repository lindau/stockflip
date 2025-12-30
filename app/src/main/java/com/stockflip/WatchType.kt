package com.stockflip

sealed class WatchType {
    data class PricePair(
        val priceDifference: Double,
        val notifyWhenEqual: Boolean
    ) : WatchType()
    
    data class PriceTarget(
        val targetPrice: Double,
        val direction: PriceDirection
    ) : WatchType()
    
    data class KeyMetrics(
        val metricType: MetricType,
        val targetValue: Double,
        val direction: PriceDirection
    ) : WatchType()
    
    data class ATHDrop(
        val dropPercentage: Double
    ) : WatchType()
    
    data class DailyHighDrop(
        val dropPercentage: Double
    ) : WatchType()
    
    enum class PriceDirection {
        ABOVE,
        BELOW
    }
    
    enum class MetricType {
        PE_RATIO,
        PS_RATIO,
        DIVIDEND_YIELD
    }
}
