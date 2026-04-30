package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupedWatchItemAdapterTest {

    @Test
    fun `overview summary active count includes active price pairs`() {
        val stockWatch = WatchItemUiState(
            WatchItem(
                id = 1,
                watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
                ticker = "AAPL",
                companyName = "Apple",
                isActive = true
            )
        )
        val pairWatch = WatchItemUiState(
            WatchItem(
                id = 2,
                watchType = WatchType.PricePair(priceDifference = 10.0, notifyWhenEqual = false),
                ticker1 = "AAPL",
                ticker2 = "MSFT",
                companyName1 = "Apple",
                companyName2 = "Microsoft",
                isActive = true
            )
        )

        val summary = buildOverviewSummaryItem(
            allItems = listOf(stockWatch, pairWatch),
            stockItems = listOf(stockWatch),
            nearTriggerCount = 0,
            today = "2026-04-30"
        )

        assertEquals(2, summary.activeCount)
    }

    @Test
    fun `overview summary triggered today count includes triggered price pairs`() {
        val today = "2026-04-30"
        val stockWatch = WatchItemUiState(
            WatchItem(
                id = 1,
                watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
                ticker = "AAPL",
                companyName = "Apple",
                isTriggered = false
            )
        )
        val pairWatch = WatchItemUiState(
            WatchItem(
                id = 2,
                watchType = WatchType.PricePair(priceDifference = 10.0, notifyWhenEqual = false),
                ticker1 = "AAPL",
                ticker2 = "MSFT",
                companyName1 = "Apple",
                companyName2 = "Microsoft",
                isTriggered = true,
                lastTriggeredDate = today
            )
        )

        val summary = buildOverviewSummaryItem(
            allItems = listOf(stockWatch, pairWatch),
            stockItems = listOf(stockWatch),
            nearTriggerCount = 0,
            today = today
        )

        assertEquals(1, summary.triggeredTodayCount)
    }

    @Test
    fun `alerts summary triggered count includes live-triggered price pairs`() {
        val pairWatch = WatchItemUiState(
            item = WatchItem(
                id = 1,
                watchType = WatchType.PricePair(priceDifference = 10.0, notifyWhenEqual = false),
                ticker1 = "AAPL",
                ticker2 = "MSFT",
                companyName1 = "Apple",
                companyName2 = "Microsoft",
                isTriggered = false
            ),
            live = LiveWatchData(
                currentPrice1 = 100.0,
                currentPrice2 = 95.0
            )
        )

        val summary = buildAlertsSummaryItem(
            summarySource = listOf(pairWatch),
            today = "2026-04-30"
        )

        assertEquals(1, summary.triggeredCount)
        assertEquals(1, summary.triggeredTodayCount)
    }
}
