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
    fun `setActive updates active flag`() {
        val item = WatchItem(
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
            ticker = "AAPL"
        )

        val updated = item.setActive(false)

        assertFalse(updated.isActive)
    }
}
