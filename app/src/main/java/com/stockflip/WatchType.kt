package com.stockflip

import androidx.room.TypeConverter

/**
 * Sealed class representing different types of stock watches.
 * This structure allows for easy extension with new watch types in the future.
 */
sealed class WatchType {
    abstract val kind: Kind

    enum class Kind(
        val displayName: String,
        val isLegacyManaged: Boolean
    ) {
        PRICE_PAIR(
            displayName = "Aktiepar",
            isLegacyManaged = true
        ),
        PRICE_TARGET(
            displayName = "Prisbevakning",
            isLegacyManaged = false
        ),
        KEY_METRICS(
            displayName = "Nyckeltal",
            isLegacyManaged = false
        ),
        ATH_BASED(
            displayName = "Drawdown",
            isLegacyManaged = false
        ),
        PRICE_RANGE(
            displayName = "Prisintervall",
            isLegacyManaged = true
        ),
        DAILY_MOVE(
            displayName = "Dagsrörelse",
            isLegacyManaged = false
        ),
        COMBINED(
            displayName = "Kombinerat larm",
            isLegacyManaged = true
        );
    }

    /**
     * Watch for when a stock pair reaches a certain price difference.
     * Hanteras fortsatt i den separata pair-vyn och är därför en legacy-typ i huvudflödet.
     */
    data class PricePair(
        val priceDifference: Double,
        val notifyWhenEqual: Boolean
    ) : WatchType() {
        override val kind: Kind = Kind.PRICE_PAIR
    }

    /**
     * Watch for when a single stock reaches a target price.
     */
    data class PriceTarget(
        val targetPrice: Double,
        val direction: PriceDirection
    ) : WatchType() {
        override val kind: Kind = Kind.PRICE_TARGET
    }

    /**
     * Watch for when a stock's key metric reaches a target value.
     */
    data class KeyMetrics(
        val metricType: MetricType,
        val targetValue: Double,
        val direction: PriceDirection
    ) : WatchType() {
        override val kind: Kind = Kind.KEY_METRICS
    }

    /**
     * Watch for when a stock has dropped from a selected high reference.
     */
    data class ATHBased(
        val dropType: DropType,
        val dropValue: Double,
        val reference: HighReference = HighReference.FIFTY_TWO_WEEK_HIGH
    ) : WatchType() {
        override val kind: Kind = Kind.ATH_BASED

        init {
            when (dropType) {
                DropType.PERCENTAGE -> require(dropValue > 0 && dropValue <= 100) {
                    "dropValue måste vara mellan 0 och 100 för PERCENTAGE"
                }
                DropType.ABSOLUTE -> require(dropValue > 0) {
                    "dropValue måste vara större än 0 för ABSOLUTE"
                }
            }
        }
    }

    /**
     * Watch for when a stock price is within a specific range.
     * Enligt PRD: "pris inom [A, B]"
     */
    data class PriceRange(
        val minPrice: Double,
        val maxPrice: Double
    ) : WatchType() {
        override val kind: Kind = Kind.PRICE_RANGE

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
        override val kind: Kind = Kind.DAILY_MOVE

        init {
            require(percentThreshold > 0) {
                "percentThreshold måste vara större än 0"
            }
        }
    }

    /**
     * Watch for combined conditions using logical operators (AND, OR, NOT).
     * Enligt PRD Fas 3: "kombinerade larm med flera villkor"
     */
    data class Combined(
        val expression: AlertExpression
    ) : WatchType() {
        override val kind: Kind = Kind.COMBINED
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
        PERCENTAGE,  // Drop in percentage from selected high
        ABSOLUTE     // Drop in absolute value from selected high
    }

    enum class HighReference {
        FIFTY_TWO_WEEK_HIGH,
        ALL_TIME_HIGH
    }

    enum class DailyMoveDirection {
        UP,     // Alert när dailyChange ≥ +percentThreshold
        DOWN,   // Alert när dailyChange ≤ -percentThreshold
        BOTH    // Alert när |dailyChange| ≥ percentThreshold
    }
}
