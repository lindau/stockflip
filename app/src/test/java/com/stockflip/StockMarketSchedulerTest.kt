package com.stockflip

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMarketSchedulerTest {

    // 2026-05-22 är en fredag (CEST, UTC+2); 2026-05-23 är en lördag.
    private val stoOpen1715 = Instant.parse("2026-05-22T15:15:00Z")   // 17:15 CEST
    private val stoGrace1745 = Instant.parse("2026-05-22T15:45:00Z")  // 17:45 CEST
    private val stoAfter1830 = Instant.parse("2026-05-22T16:30:00Z")  // 18:30 CEST
    private val stoNight2330 = Instant.parse("2026-05-22T21:30:00Z")  // 23:30 CEST
    private val stoWeekday1100 = Instant.parse("2026-05-22T09:00:00Z") // 11:00 CEST fredag
    private val stoSaturday1100 = Instant.parse("2026-05-23T09:00:00Z") // 11:00 CEST lördag
    private val usOpen2000Cest = Instant.parse("2026-05-22T18:00:00Z") // 20:00 CEST = 14:00 EDT

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

    @Test
    fun `isMarketOpenForExchange has no grace period after close`() {
        assertTrue(StockMarketScheduler.isMarketOpenForExchange("STO", stoOpen1715))
        assertFalse(StockMarketScheduler.isMarketOpenForExchange("STO", stoGrace1745))
    }

    @Test
    fun `notification window allows up to 30 minutes after close`() {
        assertTrue(StockMarketScheduler.isWithinNotificationWindowForExchange("STO", stoOpen1715))
        assertTrue(StockMarketScheduler.isWithinNotificationWindowForExchange("STO", stoGrace1745))
        assertFalse(StockMarketScheduler.isWithinNotificationWindowForExchange("STO", stoAfter1830))
        assertFalse(StockMarketScheduler.isWithinNotificationWindowForExchange("STO", stoNight2330))
        assertFalse(StockMarketScheduler.isWithinNotificationWindowForExchange("STO", stoSaturday1100))
    }

    @Test
    fun `isAnyRelevantMarketOpen blocks Swedish watch at night`() {
        val symbols = mapOf("VOLV-B.ST" to null)
        assertFalse(StockMarketScheduler.isAnyRelevantMarketOpen(symbols, stoNight2330))
        assertTrue(StockMarketScheduler.isAnyRelevantMarketOpen(symbols, stoWeekday1100))
    }

    @Test
    fun `isAnyRelevantMarketOpen always allows crypto`() {
        val symbols = mapOf("BTC-USD" to null)
        assertTrue(StockMarketScheduler.isAnyRelevantMarketOpen(symbols, stoNight2330))
    }

    @Test
    fun `isAnyRelevantMarketOpen allows when any symbol's market is open`() {
        // STO stängd 20:00 CEST men US-börsen öppen.
        val symbols = mapOf("AAPL" to "NMS", "VOLV-B.ST" to null)
        assertTrue(StockMarketScheduler.isAnyRelevantMarketOpen(symbols, usOpen2000Cest))
    }

    @Test
    fun `isAnyRelevantMarketOpen fails open on unknown exchange`() {
        val symbols = mapOf("SOMEUNKNOWN" to null)
        assertTrue(StockMarketScheduler.isAnyRelevantMarketOpen(symbols, stoNight2330))
    }

    @Test
    fun `isAnyRelevantMarketOpen allows when there are no symbols`() {
        assertTrue(StockMarketScheduler.isAnyRelevantMarketOpen(emptyMap(), stoNight2330))
    }
}
