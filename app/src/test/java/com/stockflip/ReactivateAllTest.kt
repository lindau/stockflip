package com.stockflip

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactivateAllTest {
    private fun item(id: Int, type: WatchType, triggered: Boolean = true) = WatchItem(
        id = id,
        ticker = "VOLV-B.ST",
        companyName = "Volvo",
        watchType = type,
        isTriggered = triggered,
        lastTriggeredDate = if (triggered) WatchItem.getTodayDateString() else null,
    )

    private val target = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE)
    private val insider = WatchType.InsiderBuy(createdAtMillis = 0L)

    @After
    fun tearDown() = RecheckAfterReset.clear()

    @Test
    fun `insider watches are never manually reactivatable`() {
        assertFalse(item(1, insider).isManuallyReactivatable)
        assertTrue(item(2, target).isManuallyReactivatable)
    }

    @Test
    fun `reset without guard clears trigger state`() {
        val reset = item(1, target).reactivate(currentPrice = null, keepLastTriggeredDate = false)
        assertFalse(reset.isTriggered)
        assertEquals(null, reset.lastTriggeredDate)
    }

    @Test
    fun `reset with guard keeps todays date so nothing fires until next trading day`() {
        val reset = item(1, target).reactivate(currentPrice = null, keepLastTriggeredDate = true)
        assertEquals(WatchItem.getTodayDateString(), reset.lastTriggeredDate)
    }

    @Test
    fun `awaiting recheck hides live condition until worker triggers again`() {
        val reset = item(1, target, triggered = false).copy(watchType = target)
        val live = LiveWatchData(currentPrice = 150.0, lastUpdatedAt = 1L)
        val state = WatchItemUiState(reset, live)
        assertTrue(state.isTriggeredForDisplay())

        RecheckAfterReset.mark(listOf(reset))
        assertFalse(state.isTriggeredForDisplay())

        val fired = state.copy(item = reset.copy(isTriggered = true))
        assertTrue(fired.isTriggeredForDisplay())
    }
}
