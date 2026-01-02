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
     * Använder 52-veckors högsta enligt PRD.
     */
    data class ATHBased(
        val dropType: DropType,
        val dropValue: Double
    ) : WatchType()

    /**
     * Watch for when a stock price is within a specific range.
     * Enligt PRD: "pris inom [A, B]"
     */
    data class PriceRange(
        val minPrice: Double,
        val maxPrice: Double
    ) : WatchType() {
        init {
            require(minPrice < maxPrice) {
                "minPrice måste vara mindre än maxPrice"
            }
        }
    }

    /**
     * Watch for when a stock's daily price movement exceeds a threshold.
     * Enligt PRD: "dagsförändring i % ≥ +X eller ≤ -X"
     */
    data class DailyMove(
        val percentThreshold: Double,
        val direction: DailyMoveDirection
    ) : WatchType() {
        init {
            require(percentThreshold > 0) {
                "percentThreshold måste vara större än 0"
            }
        }
    }

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
        PERCENTAGE,  // Drop in percentage from ATH (52w high)
        ABSOLUTE     // Drop in absolute value (SEK) from ATH (52w high)
    }

    enum class DailyMoveDirection {
        UP,     // Alert när dailyChange ≥ +percentThreshold
        DOWN,   // Alert när dailyChange ≤ -percentThreshold
        BOTH    // Alert när |dailyChange| ≥ percentThreshold
    }
}
