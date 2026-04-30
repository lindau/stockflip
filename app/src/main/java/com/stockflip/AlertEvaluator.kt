package com.stockflip

import android.util.Log
import kotlin.math.abs

/**
 * Central evaluator för alert-regler.
 * 
 * Enligt PRD Fas 1 ska alla AlertRule kunna evalueras via en gemensam funktion.
 * Denna klass innehåller all villkorslogik och är fri från UI och datahämtning.
 */
object AlertEvaluator {
    private const val TAG = "AlertEvaluator"
    private const val PRICE_EQUALITY_THRESHOLD = 0.01

    /**
     * Utvärderar om en alert-regel ska trigga baserat på marknadsdata.
     * 
     * @param rule Alert-regeln att utvärdera
     * @param snapshotA Marknadsdata för första aktien (eller single-stock)
     * @param snapshotB Marknadsdata för andra aktien (endast för PairSpread, kan vara null)
     * @return true om alerten ska trigga, false annars
     */
    fun evaluate(
        rule: AlertRule,
        snapshotA: MarketSnapshot,
        snapshotB: MarketSnapshot? = null
    ): Boolean {
        return try {
            when (rule) {
                is AlertRule.PairSpread -> evaluatePairSpread(rule, snapshotA, snapshotB)
                is AlertRule.SinglePrice -> evaluateSinglePrice(rule, snapshotA)
                is AlertRule.SingleDrawdownFromHigh -> evaluateSingleDrawdownFromHigh(rule, snapshotA)
                is AlertRule.SingleDailyMove -> evaluateSingleDailyMove(rule, snapshotA)
                is AlertRule.SingleKeyMetric -> evaluateSingleKeyMetric(rule, snapshotA)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating alert rule: ${e.message}", e)
            false
        }
    }

    /**
     * Utvärderar par-bevakning (PairSpread).
     * Detta är befintlig funktionalitet som måste fortsätta fungera exakt som tidigare.
     */
    private fun evaluatePairSpread(
        rule: AlertRule.PairSpread,
        snapshotA: MarketSnapshot,
        snapshotB: MarketSnapshot?
    ): Boolean {
        val priceA = snapshotA.lastPrice ?: return false
        val priceB = snapshotB?.lastPrice ?: snapshotA.previousCloseOrPriceB ?: return false
        
        val priceDifference = abs(priceA - priceB)
        
        // Larm när spread når tröskelvärdet (bara om ett mål är satt)
        if (rule.spreadTarget > 0.0 && priceDifference >= rule.spreadTarget) {
            return true
        }
        
        // Larm när priserna är lika (om notifyWhenEqual är true)
        if (rule.notifyWhenEqual && priceDifference < PRICE_EQUALITY_THRESHOLD) {
            return true
        }
        
        return false
    }

    /**
     * Utvärderar single-stock målpris-bevakning.
     * Stödjer: BELOW (≤), ABOVE (≥), och WITHIN_RANGE (pris inom [A, B]).
     */
    private fun evaluateSinglePrice(
        rule: AlertRule.SinglePrice,
        snapshot: MarketSnapshot
    ): Boolean {
        val currentPrice = snapshot.lastPrice ?: return false
        
        return when (rule.comparisonType) {
            AlertRule.PriceComparisonType.BELOW -> {
                currentPrice <= rule.priceLimit
            }
            AlertRule.PriceComparisonType.ABOVE -> {
                currentPrice >= rule.priceLimit
            }
            AlertRule.PriceComparisonType.WITHIN_RANGE -> {
                val maxPrice = rule.maxPrice ?: return false
                currentPrice >= rule.priceLimit && currentPrice <= maxPrice
            }
        }
    }

    /**
     * Utvärderar drawdown från vald toppnivå.
     * Stödjer både procentuell och absolut nedgång.
     */
    private fun evaluateSingleDrawdownFromHigh(
        rule: AlertRule.SingleDrawdownFromHigh,
        snapshot: MarketSnapshot
    ): Boolean {
        val currentPrice = snapshot.lastPrice ?: return false
        val high = when (rule.reference) {
            AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH -> snapshot.week52High
            AlertRule.HighReference.ALL_TIME_HIGH -> snapshot.allTimeHigh
        } ?: return false

        if (high <= 0) {
            return false
        }

        val effectiveHigh = if (currentPrice > high) currentPrice else high
        
        return when (rule.dropType) {
            AlertRule.DrawdownDropType.PERCENTAGE -> {
                // Beräkna procentuell nedgång
                val dropPercentage = ((effectiveHigh - currentPrice) / effectiveHigh) * 100
                // Trigger när dropPercentage >= dropValue
                dropPercentage >= rule.dropValue
            }
            AlertRule.DrawdownDropType.ABSOLUTE -> {
                // Beräkna absolut nedgång i SEK
                val dropAbsolute = effectiveHigh - currentPrice
                // Trigger när dropAbsolute >= dropValue
                dropAbsolute >= rule.dropValue
            }
        }
    }

    /**
     * Utvärderar dagsrörelse i procent.
     * Enligt PRD: "dagsförändring i % ≥ +X eller ≤ -X"
     */
    private fun evaluateSingleDailyMove(
        rule: AlertRule.SingleDailyMove,
        snapshot: MarketSnapshot
    ): Boolean {
        val dailyChangePercent = snapshot.getDailyChangePercent() ?: return false
        
        return when (rule.direction) {
            AlertRule.DailyMoveDirection.UP -> {
                // Alert när dailyChange ≥ +percentThreshold
                dailyChangePercent >= rule.percentThreshold
            }
            AlertRule.DailyMoveDirection.DOWN -> {
                // Alert när dailyChange ≤ -percentThreshold
                dailyChangePercent <= -rule.percentThreshold
            }
            AlertRule.DailyMoveDirection.BOTH -> {
                // Alert när |dailyChange| ≥ percentThreshold
                abs(dailyChangePercent) >= rule.percentThreshold
            }
        }
    }

    /**
     * Utvärderar nyckeltal-bevakning (Fas 2).
     * Stödjer P/E, P/S och Dividend Yield.
     */
    private fun evaluateSingleKeyMetric(
        rule: AlertRule.SingleKeyMetric,
        snapshot: MarketSnapshot
    ): Boolean {
        val currentMetricValue = snapshot.keyMetrics[rule.metricType] ?: return false
        
        return when (rule.direction) {
            AlertRule.PriceComparisonType.ABOVE -> {
                // Alert när metricValue ≥ targetValue
                currentMetricValue >= rule.targetValue
            }
            AlertRule.PriceComparisonType.BELOW -> {
                // Alert när metricValue ≤ targetValue
                currentMetricValue <= rule.targetValue
            }
            AlertRule.PriceComparisonType.WITHIN_RANGE -> {
                // WITHIN_RANGE stöds inte för KeyMetrics
                false
            }
        }
    }
}
