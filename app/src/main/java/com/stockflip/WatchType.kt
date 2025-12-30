package com.stockflip

import androidx.room.TypeConverter

/**
 * Sealed class representing different types of stock watches.
 * This structure allows for easy extension with new watch types in the future.
 */
sealed class WatchType {
    /**
     * Watch for when a stock pair reaches a certain price difference.
     * This is the legacy type, maintained for backward compatibility.
     */
    data class PricePair(
        val priceDifference: Double,
        val notifyWhenEqual: Boolean
    ) : WatchType()

    /**
     * Watch for when a single stock reaches a target price.
     */
    data class PriceTarget(
        val targetPrice: Double,
        val direction: PriceDirection
    ) : WatchType()

    /**
     * Watch for when a stock's key metric reaches a target value.
     */
    data class KeyMetrics(
        val metricType: MetricType,
        val targetValue: Double,
        val direction: PriceDirection
    ) : WatchType()

    /**
     * Watch for when a stock has dropped from its All-Time High (ATH).
     */
    data class ATHBased(
        val dropType: DropType,
        val dropValue: Double
    ) : WatchType()

    enum class PriceDirection {
        ABOVE,  // Alert when price goes above target
        BELOW   // Alert when price goes below target
    }

    enum class MetricType {
        PE_RATIO,           // Price-to-Earnings ratio
        PS_RATIO,           // Price-to-Sales ratio
        DIVIDEND_YIELD      // Dividend yield percentage
    }

    enum class DropType {
        PERCENTAGE,  // Drop in percentage from ATH
        ABSOLUTE     // Drop in absolute value (SEK) from ATH
    }
}
