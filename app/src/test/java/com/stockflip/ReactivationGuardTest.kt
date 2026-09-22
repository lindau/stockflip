package com.stockflip

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactivationGuardTest {

    private val today = "2024-01-01"

    private fun dailyMoveItem(lastTriggeredDate: String?) = WatchItem(
        watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
        ticker = "AAPL",
        isTriggered = true,
        lastTriggeredDate = lastTriggeredDate
    )

    private fun priceTargetItem(lastTriggeredDate: String?) = WatchItem(
        watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.BELOW),
        ticker = "AAPL",
        isTriggered = true,
        lastTriggeredDate = lastTriggeredDate
    )

    @Test
    fun `returns false without checking condition when not triggered today`() = runTest {
        var conditionCalls = 0
        var marketCalls = 0

        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = dailyMoveItem(lastTriggeredDate = "2023-12-31"),
            today = today,
            conditionCurrentlyMet = { conditionCalls++; true },
            isMarketOpen = { marketCalls++; true }
        )

        assertFalse(result)
        assertEquals(0, conditionCalls)
        assertEquals(0, marketCalls)
    }

    @Test
    fun `non-structural type triggered today always keeps guard regardless of live condition`() = runTest {
        var conditionCalls = 0
        var marketCalls = 0

        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = dailyMoveItem(lastTriggeredDate = today),
            today = today,
            conditionCurrentlyMet = { conditionCalls++; false },
            isMarketOpen = { marketCalls++; true }
        )

        assertTrue(result)
        assertEquals(0, conditionCalls)
        assertEquals(0, marketCalls)
    }

    @Test
    fun `ATHBased triggered today always keeps guard regardless of live condition`() = runTest {
        val athItem = WatchItem(
            watchType = WatchType.ATHBased(dropType = WatchType.DropType.PERCENTAGE, dropValue = 20.0),
            ticker = "AAPL",
            isTriggered = true,
            lastTriggeredDate = today
        )

        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = athItem,
            today = today,
            conditionCurrentlyMet = { false },
            isMarketOpen = { true }
        )

        assertTrue(result)
    }

    @Test
    fun `PriceTarget triggered today keeps guard when condition still met`() = runTest {
        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = priceTargetItem(lastTriggeredDate = today),
            today = today,
            conditionCurrentlyMet = { true },
            isMarketOpen = { true }
        )

        assertTrue(result)
    }

    @Test
    fun `PriceTarget triggered today keeps guard when condition unknown`() = runTest {
        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = priceTargetItem(lastTriggeredDate = today),
            today = today,
            conditionCurrentlyMet = { null },
            isMarketOpen = { true }
        )

        assertTrue(result)
    }

    @Test
    fun `PriceTarget triggered today clears guard when condition not met and market open`() = runTest {
        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = priceTargetItem(lastTriggeredDate = today),
            today = today,
            conditionCurrentlyMet = { false },
            isMarketOpen = { true }
        )

        assertFalse(result)
    }

    @Test
    fun `PriceTarget triggered today keeps guard when condition not met but market closed`() = runTest {
        val result = shouldGuardAgainstImmediateRetrigger(
            watchItem = priceTargetItem(lastTriggeredDate = today),
            today = today,
            conditionCurrentlyMet = { false },
            isMarketOpen = { false }
        )

        assertTrue(result)
    }
}
