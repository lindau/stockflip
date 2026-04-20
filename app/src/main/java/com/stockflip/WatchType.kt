package com.stockflip

import androidx.room.TypeConverter

/**
 * Sealed class representing different types of stock watches.
 * This structure allows for easy extension with new watch types in the future.
 */
sealed class WatchType {
    abstract val kind: Kind

    enum class CreationUiPolicy {
        STOCK_DETAIL_QUICK_ACTION,
        DEDICATED_PAIR_FLOW,
        EDIT_ONLY_LEGACY
    }

    enum class Kind(
        val displayName: String,
        val creationUiPolicy: CreationUiPolicy,
        val isLegacyManaged: Boolean
    ) {
        PRICE_PAIR(
            displayName = "Aktiepar",
            creationUiPolicy = CreationUiPolicy.DEDICATED_PAIR_FLOW,
            isLegacyManaged = true
        ),
        PRICE_TARGET(
            displayName = "Prisbevakning",
            creationUiPolicy = CreationUiPolicy.STOCK_DETAIL_QUICK_ACTION,
            isLegacyManaged = false
        ),
        KEY_METRICS(
            displayName = "Nyckeltal",
            creationUiPolicy = CreationUiPolicy.STOCK_DETAIL_QUICK_ACTION,
            isLegacyManaged = false
        ),
        ATH_BASED(
            displayName = "52-veckorshögsta",
            creationUiPolicy = CreationUiPolicy.STOCK_DETAIL_QUICK_ACTION,
            isLegacyManaged = false
        ),
        PRICE_RANGE(
            displayName = "Prisintervall",
            creationUiPolicy = CreationUiPolicy.EDIT_ONLY_LEGACY,
            isLegacyManaged = true
        ),
        DAILY_MOVE(
            displayName = "Dagsrörelse",
            creationUiPolicy = CreationUiPolicy.STOCK_DETAIL_QUICK_ACTION,
            isLegacyManaged = false
        ),
        COMBINED(
            displayName = "Kombinerat larm",
            creationUiPolicy = CreationUiPolicy.EDIT_ONLY_LEGACY,
            isLegacyManaged = true
        );

        val supportsStockDetailQuickCreate: Boolean
            get() = creationUiPolicy == CreationUiPolicy.STOCK_DETAIL_QUICK_ACTION

        val supportsCurrentUiCreation: Boolean
            get() = creationUiPolicy != CreationUiPolicy.EDIT_ONLY_LEGACY
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
     * Watch for when a stock has dropped from its All-Time High (ATH).
     * Använder 52-veckors högsta enligt PRD.
     */
    data class ATHBased(
        val dropType: DropType,
        val dropValue: Double
    ) : WatchType() {
        override val kind: Kind = Kind.ATH_BASED
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
        PERCENTAGE,  // Drop in percentage from ATH (52w high)
        ABSOLUTE     // Drop in absolute value (SEK) from ATH (52w high)
    }

    enum class DailyMoveDirection {
        UP,     // Alert när dailyChange ≥ +percentThreshold
        DOWN,   // Alert när dailyChange ≤ -percentThreshold
        BOTH    // Alert när |dailyChange| ≥ percentThreshold
    }
}
