package com.stockflip

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMarketSchedulerTest {

    @Test
    fun `isMarketOpenForExchange uses the exchange timezone`() {
        val duringUsTrading = Instant.parse("2026-05-22T19:00:00Z")
        val afterStockholmClose = Instant.parse("2026-05-22T16:00:00Z")

        assertTrue(StockMarketScheduler.isMarketOpenForExchange("NASDAQ", duringUsTrading))
        assertFalse(StockMarketScheduler.isMarketOpenForExchange("STO", afterStockholmClose))
    }

    @Test
    fun `isMarketOpenForSymbol infers Swedish and crypto symbols`() {
        val afterStockholmClose = Instant.parse("2026-05-22T16:00:00Z")

        assertFalse(StockMarketScheduler.isMarketOpenForSymbol("VOLV-B.ST", instant = afterStockholmClose))
        assertTrue(StockMarketScheduler.isMarketOpenForSymbol("BTC-USD", instant = afterStockholmClose))
    }
}
