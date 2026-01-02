package com.stockflip

import org.junit.Assert.*
import org.junit.Test

/**
 * Tester för AlertRuleConverter - konverterar WatchItem till AlertRule.
 */
class AlertRuleConverterTest {

    @Test
    fun `toAlertRule should convert PricePair WatchItem to PairSpread AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.PricePair(10.0, false),
            ticker1 = "AAPL",
            ticker2 = "MSFT",
            companyName1 = "Apple",
            companyName2 = "Microsoft"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert PricePair to AlertRule", alertRule)
        assertTrue("Should be PairSpread", alertRule is AlertRule.PairSpread)
        val pairSpread = alertRule as AlertRule.PairSpread
        assertEquals("AAPL", pairSpread.symbolA)
        assertEquals("MSFT", pairSpread.symbolB)
        assertEquals(10.0, pairSpread.spreadTarget, 0.01)
        assertFalse(pairSpread.notifyWhenEqual)
    }

    @Test
    fun `toAlertRule should convert PriceTarget WatchItem to SinglePrice AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert PriceTarget to AlertRule", alertRule)
        assertTrue("Should be SinglePrice", alertRule is AlertRule.SinglePrice)
        val singlePrice = alertRule as AlertRule.SinglePrice
        assertEquals("AAPL", singlePrice.symbol)
        assertEquals(AlertRule.PriceComparisonType.BELOW, singlePrice.comparisonType)
        assertEquals(100.0, singlePrice.priceLimit, 0.01)
    }

    @Test
    fun `toAlertRule should convert PriceRange WatchItem to SinglePrice WITHIN_RANGE AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.PriceRange(90.0, 110.0),
            ticker = "AAPL",
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert PriceRange to AlertRule", alertRule)
        assertTrue("Should be SinglePrice", alertRule is AlertRule.SinglePrice)
        val singlePrice = alertRule as AlertRule.SinglePrice
        assertEquals("AAPL", singlePrice.symbol)
        assertEquals(AlertRule.PriceComparisonType.WITHIN_RANGE, singlePrice.comparisonType)
        assertEquals(90.0, singlePrice.priceLimit, 0.01)
        assertEquals(110.0, singlePrice.maxPrice!!, 0.01)
    }

    @Test
    fun `toAlertRule should convert ATHBased PERCENTAGE WatchItem to SingleDrawdownFromHigh AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 10.0),
            ticker = "AAPL",
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert ATHBased to AlertRule", alertRule)
        assertTrue("Should be SingleDrawdownFromHigh", alertRule is AlertRule.SingleDrawdownFromHigh)
        val drawdown = alertRule as AlertRule.SingleDrawdownFromHigh
        assertEquals("AAPL", drawdown.symbol)
        assertEquals(AlertRule.DrawdownDropType.PERCENTAGE, drawdown.dropType)
        assertEquals(10.0, drawdown.dropValue, 0.01)
    }

    @Test
    fun `toAlertRule should convert DailyMove WatchItem to SingleDailyMove AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = "AAPL",
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert DailyMove to AlertRule", alertRule)
        assertTrue("Should be SingleDailyMove", alertRule is AlertRule.SingleDailyMove)
        val dailyMove = alertRule as AlertRule.SingleDailyMove
        assertEquals("AAPL", dailyMove.symbol)
        assertEquals(5.0, dailyMove.percentThreshold, 0.01)
        assertEquals(AlertRule.DailyMoveDirection.UP, dailyMove.direction)
    }

    @Test
    fun `toAlertRule should convert KeyMetrics WatchItem to SingleKeyMetric AlertRule`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.KeyMetrics(
                WatchType.MetricType.PE_RATIO,
                20.0,
                WatchType.PriceDirection.ABOVE
            ),
            ticker = "AAPL",
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNotNull("Should convert KeyMetrics to AlertRule", alertRule)
        assertTrue("Should be SingleKeyMetric", alertRule is AlertRule.SingleKeyMetric)
        val keyMetric = alertRule as AlertRule.SingleKeyMetric
        assertEquals("AAPL", keyMetric.symbol)
        assertEquals(AlertRule.KeyMetricType.PE_RATIO, keyMetric.metricType)
        assertEquals(20.0, keyMetric.targetValue, 0.01)
        assertEquals(AlertRule.PriceComparisonType.ABOVE, keyMetric.direction)
    }

    @Test
    fun `toAlertRule should return null when ticker is missing`() {
        // Given
        val watchItem = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = null,
            companyName = "Apple"
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(watchItem)

        // Then
        assertNull("Should return null when ticker is missing", alertRule)
    }

    @Test
    fun `toAlertRule should convert StockPair to PairSpread AlertRule`() {
        // Given
        val stockPair = StockPair(
            ticker1 = "AAPL",
            ticker2 = "MSFT",
            companyName1 = "Apple",
            companyName2 = "Microsoft",
            priceDifference = 10.0,
            notifyWhenEqual = false
        )

        // When
        val alertRule = AlertRuleConverter.toAlertRule(stockPair)

        // Then
        assertTrue("Should be PairSpread", alertRule is AlertRule.PairSpread)
        val pairSpread = alertRule as AlertRule.PairSpread
        assertEquals("AAPL", pairSpread.symbolA)
        assertEquals("MSFT", pairSpread.symbolB)
        assertEquals(10.0, pairSpread.spreadTarget, 0.01)
        assertFalse(pairSpread.notifyWhenEqual)
    }
}
