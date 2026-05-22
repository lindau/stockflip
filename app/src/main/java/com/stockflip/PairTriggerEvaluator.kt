package com.stockflip

import kotlin.math.abs

enum class PairTriggerSide {
    TICKER1_OVER,
    TICKER2_OVER,
    EQUAL
}

data class PairTriggerResult(
    val side: PairTriggerSide,
    val signedSpread: Double,
    val absoluteSpread: Double
)

object PairTriggerEvaluator {
    const val PRICE_EQUALITY_THRESHOLD = 0.01

    fun evaluate(
        priceA: Double?,
        priceB: Double?,
        spreadTarget: Double,
        notifyWhenEqual: Boolean
    ): PairTriggerResult? {
        if (priceA == null || priceB == null) return null

        val signedSpread = priceA - priceB
        val absoluteSpread = abs(signedSpread)

        if (notifyWhenEqual && absoluteSpread < PRICE_EQUALITY_THRESHOLD) {
            return PairTriggerResult(PairTriggerSide.EQUAL, signedSpread, absoluteSpread)
        }

        if (spreadTarget > 0.0 && absoluteSpread >= spreadTarget) {
            val side = if (signedSpread > 0.0) {
                PairTriggerSide.TICKER1_OVER
            } else {
                PairTriggerSide.TICKER2_OVER
            }
            return PairTriggerResult(side, signedSpread, absoluteSpread)
        }

        return null
    }
}
