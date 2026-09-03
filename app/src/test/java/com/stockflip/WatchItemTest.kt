package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchItemTest {

    @Test
    fun `canTrigger returns false when inactive`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            isActive = false
        )

        assertFalse(item.canTrigger("2024-01-01"))
    }

    @Test
    fun `canTrigger returns false when already triggered`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            isTriggered = true
        )

        assertFalse(item.canTrigger("2024-01-01"))
    }

    @Test
    fun `canTrigger returns false when triggered today`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            lastTriggeredDate = "2024-01-01"
        )

        assertFalse(item.canTrigger("2024-01-01"))
    }

    @Test
    fun `canTrigger returns true when active and not triggered`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL"
        )

        assertTrue(item.canTrigger("2024-01-01"))
    }

    @Test
    fun `markAsTriggered updates lastTriggeredDate and isTriggered`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL"
        )

        val updated = item.markAsTriggered("2024-01-01")

        assertTrue(updated.isTriggered)
        assertEquals("2024-01-01", updated.lastTriggeredDate)
    }

    @Test
    fun `reactivate clears triggered flag`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            isTriggered = true
        )

        val updated = item.reactivate()

        assertFalse(updated.isTriggered)
    }

    @Test
    fun `reactivate sets alert active`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false
        )

        val updated = item.reactivate()

        assertTrue(updated.isActive)
    }

    @Test
    fun `reactivate sets price target direction below when current price is above target`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false
        )

        val updated = item.reactivate(currentPrice = 105.0)
        val updatedType = updated.watchType as WatchType.PriceTarget

        assertEquals(WatchType.PriceDirection.BELOW, updatedType.direction)
        assertTrue(updated.isActive)
        assertFalse(updated.isTriggered)
    }

    @Test
    fun `reactivate sets price target direction above when current price is below target`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false
        )

        val updated = item.reactivate(currentPrice = 95.0)
        val updatedType = updated.watchType as WatchType.PriceTarget

        assertEquals(WatchType.PriceDirection.ABOVE, updatedType.direction)
    }

    @Test
    fun `reactivate can keep last triggered date while recalculating price target direction`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false,
            lastTriggeredDate = "2024-01-01"
        )

        val updated = item.reactivate(currentPrice = 105.0, keepLastTriggeredDate = true)
        val updatedType = updated.watchType as WatchType.PriceTarget

        assertEquals(WatchType.PriceDirection.BELOW, updatedType.direction)
        assertTrue(updated.isActive)
        assertFalse(updated.isTriggered)
        assertEquals("2024-01-01", updated.lastTriggeredDate)
        assertFalse(updated.canTrigger("2024-01-01"))
    }

    @Test
    fun `price pair can trigger same day when spread side changes`() {
        val item = WatchItem(
            watchType = WatchType.PricePair(5.0, false),
            ticker1 = "AAPL",
            ticker2 = "MSFT",
            lastTriggeredDate = "2024-01-01",
            lastPairTriggerSide = PairTriggerSide.TICKER2_OVER.name,
            activePairTriggerSide = PairTriggerSide.TICKER2_OVER.name,
            isTriggered = true
        )

        assertFalse(item.canTrigger("2024-01-01", PairTriggerSide.TICKER2_OVER))
        assertTrue(item.canTrigger("2024-01-01", PairTriggerSide.TICKER1_OVER))
    }

    @Test
    fun `markAsTriggered stores price pair trigger side`() {
        val item = WatchItem(
            watchType = WatchType.PricePair(5.0, false),
            ticker1 = "AAPL",
            ticker2 = "MSFT"
        )

        val updated = item.markAsTriggered("2024-01-01", PairTriggerSide.TICKER1_OVER)

        assertEquals(PairTriggerSide.TICKER1_OVER.name, updated.lastPairTriggerSide)
        assertEquals(PairTriggerSide.TICKER1_OVER.name, updated.activePairTriggerSide)
        assertEquals("2024-01-01", updated.lastTriggeredDate)
        assertTrue(updated.isTriggered)
    }

    @Test
    fun `clearActivePairTriggerSide rearms same side without changing last trigger marker`() {
        val item = WatchItem(
            watchType = WatchType.PricePair(5.0, false),
            ticker1 = "AAPL",
            ticker2 = "MSFT",
            lastTriggeredDate = "2024-01-01",
            lastPairTriggerSide = PairTriggerSide.TICKER2_OVER.name,
            activePairTriggerSide = PairTriggerSide.TICKER2_OVER.name,
            isTriggered = true
        )

        val updated = item.clearActivePairTriggerSide()

        assertEquals(PairTriggerSide.TICKER2_OVER.name, updated.lastPairTriggerSide)
        assertEquals(null, updated.activePairTriggerSide)
        assertTrue(updated.canTrigger("2024-01-01", PairTriggerSide.TICKER2_OVER))
    }

    @Test
    fun `setActive updates active flag`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL"
        )

        val updated = item.setActive(false)

        assertFalse(updated.isActive)
    }

    @Test
    fun `reactivate with keepLastTriggeredDate blocks ATH alarm same day but allows next day`() {
        val item = WatchItem(
            watchType = WatchType.ATHBased(
                dropType = WatchType.DropType.PERCENTAGE,
                dropValue = 20.0
            ),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false,
            lastTriggeredDate = "2024-01-01"
        )

        val updated = item.reactivate(keepLastTriggeredDate = true)

        assertTrue(updated.isActive)
        assertFalse(updated.isTriggered)
        assertEquals("2024-01-01", updated.lastTriggeredDate)
        // Får inte kunna trigga igen samma handelsdag när villkoret fortfarande gäller.
        assertFalse(updated.canTrigger("2024-01-01"))
        // Nästa handelsdag utvärderas det på nytt.
        assertTrue(updated.canTrigger("2024-01-02"))
    }

    @Test
    fun `reactivate without keepLastTriggeredDate fully re-arms ATH alarm`() {
        val item = WatchItem(
            watchType = WatchType.ATHBased(
                dropType = WatchType.DropType.PERCENTAGE,
                dropValue = 20.0
            ),
            ticker = "AAPL",
            isTriggered = true,
            isActive = false,
            lastTriggeredDate = "2024-01-01"
        )

        val updated = item.reactivate(keepLastTriggeredDate = false)

        assertTrue(updated.isActive)
        assertFalse(updated.isTriggered)
        assertEquals(null, updated.lastTriggeredDate)
        assertTrue(updated.canTrigger("2024-01-01"))
    }
}
