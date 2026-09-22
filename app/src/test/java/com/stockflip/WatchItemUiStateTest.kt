package com.stockflip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchItemUiStateTest {

    @Test
    fun `reactivated DailyMove alert is not shown as triggered even if live move still exceeds threshold`() {
        val today = WatchItem.getTodayDateString()
        val item = WatchItem(
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = "AAPL",
            isTriggered = false,
            lastTriggeredDate = today,
            isActive = true
        )
        val uiState = WatchItemUiState(
            item = item,
            live = LiveWatchData(currentDailyChangePercent = 7.5, lastUpdatedAt = 1L)
        )

        assertTrue(uiState.hasLiveTriggerCondition())
        assertTrue(item.hasPendingNextTradingDayGuard())
        assertFalse(uiState.isTriggeredForDisplay())
    }

    @Test
    fun `DailyMove alert with cleared lastTriggeredDate reappears as triggered from live condition`() {
        val item = WatchItem(
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = "AAPL",
            isTriggered = false,
            lastTriggeredDate = null,
            isActive = true
        )
        val uiState = WatchItemUiState(
            item = item,
            live = LiveWatchData(currentDailyChangePercent = 7.5, lastUpdatedAt = 1L)
        )

        // Reproducerar det ursprungliga symptomet: utan datumspärren "flimrar" visningen
        // tillbaka till utlöst så fort livevillkoret pendlar tillbaka över tröskeln — det är
        // precis detta ReactivationGuard.shouldGuardAgainstImmediateRetrigger nu förhindrar
        // genom att alltid behålla lastTriggeredDate för icke-strukturellt justerade typer.
        assertFalse(item.hasPendingNextTradingDayGuard())
        assertTrue(uiState.isTriggeredForDisplay())
    }

    @Test
    fun `next trading day DailyMove alert is triggered for display again once actually re-marked`() {
        val item = WatchItem(
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = "AAPL",
            isTriggered = true,
            lastTriggeredDate = "2024-01-02",
            isActive = true
        )
        val uiState = WatchItemUiState(item = item)

        assertFalse(item.hasPendingNextTradingDayGuard("2024-01-03"))
        assertTrue(uiState.isTriggeredForDisplay())
    }
}
