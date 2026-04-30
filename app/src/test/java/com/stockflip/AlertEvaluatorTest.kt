package com.stockflip

import org.junit.Assert.*
import org.junit.Test

/**
 * Tester för AlertEvaluator - centraliserad logik för alert-utvärdering.
 */
class AlertEvaluatorTest {

    @Test
    fun `evaluate PairSpread should return true when spread is reached`() {
        // Given
        val rule = AlertRule.PairSpread(
            symbolA = "AAPL",
            symbolB = "MSFT",
            spreadTarget = 10.0,
            notifyWhenEqual = false
        )
        val snapshot = MarketSnapshot.forPair(100.0, 90.0) // Diff = 10.0

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when spread is reached", result)
    }

    @Test
    fun `evaluate PairSpread should return false when spread is not reached`() {
        // Given
        val rule = AlertRule.PairSpread(
            symbolA = "AAPL",
            symbolB = "MSFT",
            spreadTarget = 10.0,
            notifyWhenEqual = false
        )
        val snapshot = MarketSnapshot.forPair(100.0, 95.0) // Diff = 5.0

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when spread is not reached", result)
    }

    @Test
    fun `evaluate PairSpread should return true when prices are equal and notifyWhenEqual is true`() {
        // Given
        val rule = AlertRule.PairSpread(
            symbolA = "AAPL",
            symbolB = "MSFT",
            spreadTarget = 10.0,
            notifyWhenEqual = true
        )
        val snapshot = MarketSnapshot.forPair(100.0, 100.0) // Equal prices

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when prices are equal and notifyWhenEqual is true", result)
    }

    @Test
    fun `evaluate SinglePrice BELOW should return true when price is below limit`() {
        // Given
        val rule = AlertRule.SinglePrice(
            symbol = "AAPL",
            comparisonType = AlertRule.PriceComparisonType.BELOW,
            priceLimit = 100.0
        )
        val snapshot = MarketSnapshot.forSingleStock(90.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when price is below limit", result)
    }

    @Test
    fun `evaluate SinglePrice BELOW should return false when price is above limit`() {
        // Given
        val rule = AlertRule.SinglePrice(
            symbol = "AAPL",
            comparisonType = AlertRule.PriceComparisonType.BELOW,
            priceLimit = 100.0
        )
        val snapshot = MarketSnapshot.forSingleStock(110.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when price is above limit", result)
    }

    @Test
    fun `evaluate SinglePrice ABOVE should return true when price is above limit`() {
        // Given
        val rule = AlertRule.SinglePrice(
            symbol = "AAPL",
            comparisonType = AlertRule.PriceComparisonType.ABOVE,
            priceLimit = 100.0
        )
        val snapshot = MarketSnapshot.forSingleStock(110.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when price is above limit", result)
    }

    @Test
    fun `evaluate SinglePrice WITHIN_RANGE should return true when price is within range`() {
        // Given
        val rule = AlertRule.SinglePrice(
            symbol = "AAPL",
            comparisonType = AlertRule.PriceComparisonType.WITHIN_RANGE,
            priceLimit = 90.0,
            maxPrice = 110.0
        )
        val snapshot = MarketSnapshot.forSingleStock(100.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when price is within range", result)
    }

    @Test
    fun `evaluate SinglePrice WITHIN_RANGE should return false when price is outside range`() {
        // Given
        val rule = AlertRule.SinglePrice(
            symbol = "AAPL",
            comparisonType = AlertRule.PriceComparisonType.WITHIN_RANGE,
            priceLimit = 90.0,
            maxPrice = 110.0
        )
        val snapshot = MarketSnapshot.forSingleStock(120.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when price is outside range", result)
    }

    @Test
    fun `evaluate SingleDrawdownFromHigh should return true when drawdown is reached`() {
        // Given
        val rule = AlertRule.SingleDrawdownFromHigh(
            symbol = "AAPL",
            dropType = AlertRule.DrawdownDropType.PERCENTAGE,
            dropValue = 10.0 // 10% drop
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 90.0, // Current price
            previousClose = null,
            week52High = 100.0 // 52w high
        ) // Drawdown = 10%

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when drawdown is reached", result)
    }

    @Test
    fun `evaluate SingleDrawdownFromHigh should return false when drawdown is not reached`() {
        // Given
        val rule = AlertRule.SingleDrawdownFromHigh(
            symbol = "AAPL",
            dropType = AlertRule.DrawdownDropType.PERCENTAGE,
            dropValue = 10.0 // 10% drop
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 95.0, // Current price
            previousClose = null,
            week52High = 100.0 // 52w high
        ) // Drawdown = 5%

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when drawdown is not reached", result)
    }

    @Test
    fun `evaluate SingleDrawdownFromHigh should return false when week52High is null`() {
        // Given
        val rule = AlertRule.SingleDrawdownFromHigh(
            symbol = "AAPL",
            dropType = AlertRule.DrawdownDropType.PERCENTAGE,
            dropValue = 10.0
        )
        val snapshot = MarketSnapshot.forSingleStock(90.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when week52High is null", result)
    }

    @Test
    fun `evaluate SingleDrawdownFromHigh should return true when all-time high drawdown is reached`() {
        val rule = AlertRule.SingleDrawdownFromHigh(
            symbol = "AAPL",
            dropType = AlertRule.DrawdownDropType.PERCENTAGE,
            dropValue = 25.0,
            reference = AlertRule.HighReference.ALL_TIME_HIGH
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 75.0,
            previousClose = null,
            allTimeHigh = 100.0
        )

        val result = AlertEvaluator.evaluate(rule, snapshot)

        assertTrue("Should trigger when all-time-high drawdown is reached", result)
    }

    @Test
    fun `evaluate SingleDrawdownFromHigh should return false when allTimeHigh is null`() {
        val rule = AlertRule.SingleDrawdownFromHigh(
            symbol = "AAPL",
            dropType = AlertRule.DrawdownDropType.PERCENTAGE,
            dropValue = 25.0,
            reference = AlertRule.HighReference.ALL_TIME_HIGH
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 75.0,
            previousClose = null
        )

        val result = AlertEvaluator.evaluate(rule, snapshot)

        assertFalse("Should not trigger when allTimeHigh is null", result)
    }

    @Test
    fun `evaluate SingleDailyMove UP should return true when daily change is above threshold`() {
        // Given
        val rule = AlertRule.SingleDailyMove(
            symbol = "AAPL",
            percentThreshold = 5.0, // 5% threshold
            direction = AlertRule.DailyMoveDirection.UP
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 105.0,
            previousClose = 100.0, // 5% increase
            week52High = null
        )

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when daily change is above threshold", result)
    }

    @Test
    fun `evaluate SingleDailyMove UP should return false when daily change is below threshold`() {
        // Given
        val rule = AlertRule.SingleDailyMove(
            symbol = "AAPL",
            percentThreshold = 5.0,
            direction = AlertRule.DailyMoveDirection.UP
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 103.0,
            previousClose = 100.0, // 3% increase
            week52High = null
        )

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when daily change is below threshold", result)
    }

    @Test
    fun `evaluate SingleDailyMove DOWN should return true when daily change is below negative threshold`() {
        // Given
        val rule = AlertRule.SingleDailyMove(
            symbol = "AAPL",
            percentThreshold = 5.0,
            direction = AlertRule.DailyMoveDirection.DOWN
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 95.0,
            previousClose = 100.0, // -5% decrease
            week52High = null
        )

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when daily change is below negative threshold", result)
    }

    @Test
    fun `evaluate SingleDailyMove BOTH should return true when absolute change is above threshold`() {
        // Given
        val rule = AlertRule.SingleDailyMove(
            symbol = "AAPL",
            percentThreshold = 5.0,
            direction = AlertRule.DailyMoveDirection.BOTH
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 95.0,
            previousClose = 100.0, // -5% decrease
            week52High = null
        )

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when absolute change is above threshold", result)
    }

    @Test
    fun `evaluate SingleDailyMove should return false when previousClose is null`() {
        // Given
        val rule = AlertRule.SingleDailyMove(
            symbol = "AAPL",
            percentThreshold = 5.0,
            direction = AlertRule.DailyMoveDirection.UP
        )
        val snapshot = MarketSnapshot.forSingleStock(105.0, null, null)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertFalse("Should not trigger when previousClose is null", result)
    }

    @Test
    fun `evaluate SingleKeyMetric earnings per share ABOVE should return true when value is above target`() {
        val rule = AlertRule.SingleKeyMetric(
            symbol = "AAPL",
            metricType = AlertRule.KeyMetricType.EARNINGS_PER_SHARE,
            targetValue = 8.5,
            direction = AlertRule.PriceComparisonType.ABOVE
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = null,
            keyMetrics = mapOf(AlertRule.KeyMetricType.EARNINGS_PER_SHARE to 9.0)
        )

        val result = AlertEvaluator.evaluate(rule, snapshot)

        assertTrue("Should trigger when EPS is above target", result)
    }

    @Test
    fun `evaluate SingleKeyMetric earnings per share BELOW should return false when value is above target`() {
        val rule = AlertRule.SingleKeyMetric(
            symbol = "AAPL",
            metricType = AlertRule.KeyMetricType.EARNINGS_PER_SHARE,
            targetValue = 8.5,
            direction = AlertRule.PriceComparisonType.BELOW
        )
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = null,
            keyMetrics = mapOf(AlertRule.KeyMetricType.EARNINGS_PER_SHARE to 9.0)
        )

        val result = AlertEvaluator.evaluate(rule, snapshot)

        assertFalse("Should not trigger when EPS is above a below-target alert", result)
    }
}
