package com.stockflip.ui.stockdetail

import com.stockflip.StockDetailData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDetailModelTest {
    private fun data(high: Double? = 200.0) = StockDetailData(
        symbol = "X", companyName = "X", lastPrice = 150.0, previousClose = 149.0,
        week52High = high, week52Low = 100.0, dailyChangePercent = 0.5, drawdownPercent = null,
        allTimeHigh = 300.0,
    )

    private fun alert(type: WatchType, active: Boolean = true) =
        WatchItemUiState(WatchItem(id = 1, ticker = "X", watchType = type, isActive = active))

    @Test fun priceTargetGivesOneLevel() {
        val levels = watchLevelsFor(listOf(alert(WatchType.PriceTarget(120.0, WatchType.PriceDirection.BELOW))), data())
        assertEquals(listOf(120.0), levels.map { it.price })
    }

    @Test fun athPercentageUsesReferenceHigh() {
        val a = alert(WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 10.0))
        assertEquals(180.0, watchLevelsFor(listOf(a), data()).single().price, 0.001)
        val b = alert(WatchType.ATHBased(WatchType.DropType.ABSOLUTE, 50.0, WatchType.HighReference.ALL_TIME_HIGH))
        assertEquals(250.0, watchLevelsFor(listOf(b), data()).single().price, 0.001)
    }

    @Test fun inactiveAndMissingHighAreSkipped() {
        val paused = alert(WatchType.PriceTarget(120.0, WatchType.PriceDirection.BELOW), active = false)
        val ath = alert(WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 10.0))
        assertTrue(watchLevelsFor(listOf(paused, ath), data(high = null)).isEmpty())
    }

    @Test fun keyFiguresOmitMissingValues() {
        val figures = keyFiguresFor(data().copy(peRatio = 12.34, dividendYield = null))
        assertEquals(listOf("P/E"), figures.map { it.label })
        assertEquals("12,3", figures.single().value)
    }
}
