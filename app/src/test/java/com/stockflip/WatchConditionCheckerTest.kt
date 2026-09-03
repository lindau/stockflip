package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tester för WatchConditionChecker - används av återaktiveringslogiken för att avgöra om ett
 * larms villkor fortfarande är uppfyllt (och därmed måste datumspärras vid återaktivering).
 */
class WatchConditionCheckerTest {

    private fun priceTarget(target: Double) = WatchItem(
        watchType = WatchType.PriceTarget(target, WatchType.PriceDirection.ABOVE),
        ticker = "AAPL"
    )

    private fun drawdown(dropPercent: Double) = WatchItem(
        watchType = WatchType.ATHBased(
            dropType = WatchType.DropType.PERCENTAGE,
            dropValue = dropPercent
        ),
        ticker = "AAPL"
    )

    @Test
    fun `price target still met returns true`() {
        val snapshot = MarketSnapshot.forSingleStock(lastPrice = 210.0, previousClose = 200.0)

        assertEquals(true, WatchConditionChecker.isConditionCurrentlyMet(priceTarget(200.0), snapshot))
    }

    @Test
    fun `price target no longer met returns false`() {
        val snapshot = MarketSnapshot.forSingleStock(lastPrice = 190.0, previousClose = 200.0)

        assertEquals(false, WatchConditionChecker.isConditionCurrentlyMet(priceTarget(200.0), snapshot))
    }

    @Test
    fun `missing last price returns null`() {
        val snapshot = MarketSnapshot.forSingleStock(lastPrice = null, previousClose = 200.0)

        assertNull(WatchConditionChecker.isConditionCurrentlyMet(priceTarget(200.0), snapshot))
    }

    @Test
    fun `drawdown still in effect returns true`() {
        // 30 % drawdown från 100 -> pris 70, tröskel 20 %.
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 70.0,
            previousClose = 72.0,
            week52High = 100.0
        )

        assertEquals(true, WatchConditionChecker.isConditionCurrentlyMet(drawdown(20.0), snapshot))
    }

    @Test
    fun `drawdown recovered returns false`() {
        // 5 % drawdown, tröskel 20 %.
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 95.0,
            previousClose = 94.0,
            week52High = 100.0
        )

        assertEquals(false, WatchConditionChecker.isConditionCurrentlyMet(drawdown(20.0), snapshot))
    }

    @Test
    fun `drawdown without high reference returns null`() {
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 70.0,
            previousClose = 72.0,
            week52High = null
        )

        assertNull(WatchConditionChecker.isConditionCurrentlyMet(drawdown(20.0), snapshot))
    }

    @Test
    fun `insider buy watch cannot be evaluated and returns null`() {
        val item = WatchItem(watchType = WatchType.InsiderBuy(), ticker = "AAPL")
        val snapshot = MarketSnapshot.forSingleStock(lastPrice = 100.0, previousClose = 100.0)

        assertNull(WatchConditionChecker.isConditionCurrentlyMet(item, snapshot))
    }

    @Test
    fun `guard semantics - reactivating while still met keeps date barrier`() {
        val snapshot = MarketSnapshot.forSingleStock(lastPrice = 210.0, previousClose = 200.0)
        val item = priceTarget(200.0).copy(
            isTriggered = true,
            isActive = false,
            lastTriggeredDate = "2024-01-01"
        )

        val stillMet = WatchConditionChecker.isConditionCurrentlyMet(item, snapshot)
        assertTrue(stillMet == true)

        val reactivated = item.reactivate(currentPrice = null, keepLastTriggeredDate = stillMet == true)
        assertFalse(reactivated.canTrigger("2024-01-01"))
        assertTrue(reactivated.canTrigger("2024-01-02"))
    }
}
