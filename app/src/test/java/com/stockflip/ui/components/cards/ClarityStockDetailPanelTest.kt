package com.stockflip.ui.components.cards

import com.stockflip.ChartPeriod
import com.stockflip.IntradayChartData
import com.stockflip.StockDetailData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClarityStockDetailPanelTest {

    @Test
    fun `non-day period change uses selected chart range`() {
        val data = stockData(
            lastPrice = 105.0,
            previousClose = 100.0,
            dailyChangePercent = 5.0,
        )
        val chartData = IntradayChartData(
            timestamps = listOf(1L, 2L, 3L),
            prices = listOf(80.0, 90.0, 100.0),
            previousClose = 100.0,
        )

        val change = calculatePeriodChange(data, chartData, ChartPeriod.MONTH)

        assertEquals(20.0, change.delta!!, 0.0001)
        assertEquals(25.0, change.percent!!, 0.0001)
    }

    @Test
    fun `day period change uses previous close base`() {
        val data = stockData(
            lastPrice = 110.0,
            previousClose = 100.0,
        )
        val chartData = IntradayChartData(
            timestamps = listOf(1L, 2L),
            prices = listOf(104.0, 110.0),
            previousClose = 100.0,
        )

        val change = calculatePeriodChange(data, chartData, ChartPeriod.DAY)

        assertEquals(10.0, change.delta!!, 0.0001)
        assertEquals(10.0, change.percent!!, 0.0001)
    }

    @Test
    fun `non-day fallback chart data does not report flat performance`() {
        val data = stockData(lastPrice = 110.0, previousClose = 100.0)
        val chartData = IntradayChartData(
            timestamps = listOf(1L, 2L),
            prices = listOf(110.0, 110.0),
            previousClose = 100.0,
            emptyReason = "Marknaden stängd",
        )

        val change = calculatePeriodChange(data, chartData, ChartPeriod.WEEK)

        assertNull(change.delta)
        assertNull(change.percent)
    }

    private fun stockData(
        lastPrice: Double?,
        previousClose: Double?,
        dailyChangePercent: Double? = null,
    ): StockDetailData = StockDetailData(
        symbol = "VOLV-B.ST",
        companyName = "Volvo B",
        lastPrice = lastPrice,
        previousClose = previousClose,
        week52High = null,
        week52Low = null,
        currency = "SEK",
        exchange = "STO",
        dailyChangePercent = dailyChangePercent,
        drawdownPercent = null,
    )
}
