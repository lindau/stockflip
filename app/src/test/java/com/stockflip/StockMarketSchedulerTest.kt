package com.stockflip

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `index symbols follow their home exchange hours`() {
        // 20:00 CEST: Stockholm stängd, USA öppen
        assertFalse(StockMarketScheduler.isMarketOpenForSymbol("^OMXS30", instant = usOpen2000Cest))
        assertTrue(StockMarketScheduler.isMarketOpenForSymbol("^GSPC", instant = usOpen2000Cest))
        assertTrue(StockMarketScheduler.isMarketOpenForSymbol("^OMXS30", instant = stoWeekday1100))
        assertFalse(StockMarketScheduler.isMarketOpenForSymbol("^OMXS30", instant = stoSaturday1100))
    }

    @Test
    fun `known index symbol overrides Yahoo exchange code`() {
        // Yahoo rapporterar ^OMXS30 med exchangeName NIM
        assertFalse(StockMarketScheduler.isMarketOpenForSymbol("^OMXS30", "NIM", instant = usOpen2000Cest))
        assertFalse(StockMarketScheduler.isAnyRelevantMarketOpen(mapOf("^OMXS30" to "NIM"), stoNight2330))
    }

    @Test
    fun `Yahoo index exchange codes map to US hours`() {
        listOf("SNP", "DJI", "WCB", "CXI").forEach { code ->
            assertTrue(code, StockMarketScheduler.isMarketOpenForExchange(code, usOpen2000Cest))
            assertFalse(code, StockMarketScheduler.isMarketOpenForExchange(code, stoWeekday1100))
        }
    }

    @Test
    fun `isIndexSymbol recognises caret prefix only`() {
        assertTrue(StockSearchResult.isIndexSymbol("^OMX"))
        assertFalse(StockSearchResult.isIndexSymbol("VOLV-B.ST"))
        assertTrue(StockSearchResult.isNonEquitySymbol("^GSPC"))
        assertTrue(StockSearchResult.isNonEquitySymbol("BTC-USD"))
        assertFalse(StockSearchResult.isNonEquitySymbol("AAPL"))
    }

    private fun stoMillis(date: String, time: String): Long =
        java.time.LocalDateTime.parse("${date}T$time")
            .atZone(java.time.ZoneId.of("Europe/Stockholm")).toInstant().toEpochMilli()

    private fun stoInstant(date: String, time: String) =
        java.time.Instant.ofEpochMilli(stoMillis(date, time))

    @Test
    fun `triggerBlockedUntil evening blocks until next weekday 0915`() {
        // Tisdag 2026-10-06 kväll → onsdag 09:15
        val blocked = StockMarketScheduler.triggerBlockedUntil("VOLV-B.ST", instant = stoInstant("2026-10-06", "20:00"))
        assertEquals(stoMillis("2026-10-07", "09:15"), blocked)
    }

    @Test
    fun `triggerBlockedUntil Friday evening blocks until Monday 0915`() {
        val blocked = StockMarketScheduler.triggerBlockedUntil("VOLV-B.ST", instant = stoInstant("2026-10-09", "20:00"))
        assertEquals(stoMillis("2026-10-12", "09:15"), blocked)
    }

    @Test
    fun `triggerBlockedUntil before open and between open and first quote blocks until 0915 same day`() {
        assertEquals(stoMillis("2026-10-07", "09:15"),
            StockMarketScheduler.triggerBlockedUntil("VOLV-B.ST", instant = stoInstant("2026-10-07", "07:30")))
        assertEquals(stoMillis("2026-10-07", "09:15"),
            StockMarketScheduler.triggerBlockedUntil("VOLV-B.ST", instant = stoInstant("2026-10-07", "09:05")))
    }

    @Test
    fun `triggerBlockedUntil is null while trading, for crypto and unknown exchange`() {
        assertNull(StockMarketScheduler.triggerBlockedUntil("VOLV-B.ST", instant = stoInstant("2026-10-07", "11:00")))
        assertNull(StockMarketScheduler.triggerBlockedUntil("BTC-USD", instant = stoInstant("2026-10-07", "03:00")))
        assertNull(StockMarketScheduler.triggerBlockedUntil("XYZ", instant = stoInstant("2026-10-07", "03:00")))
    }

    private fun stoSeconds(date: String, time: String): Long = stoMillis(date, time) / 1000

    @Test
    fun `isQuoteFromLatestSession compares quote day with latest expected session`() {
        val mondayNoon = stoInstant("2026-10-05", "12:00")
        // Illikvid aktie: senaste affär onsdagen innan → gammal.
        assertEquals(false, StockMarketScheduler.isQuoteFromLatestSession(
            "PLEJD.ST", null, null, stoSeconds("2026-09-30", "17:29"), mondayNoon))
        // Affär idag → aktuell.
        assertEquals(true, StockMarketScheduler.isQuoteFromLatestSession(
            "PLEJD.ST", null, null, stoSeconds("2026-10-05", "11:55"), mondayNoon))
        // Måndag 09:05 (första kursen ej kommen): fredagens kurs räknas som aktuell.
        assertEquals(true, StockMarketScheduler.isQuoteFromLatestSession(
            "VOLV-B.ST", null, null, stoSeconds("2026-10-02", "17:29"), stoInstant("2026-10-05", "09:05")))
        // Söndag: fredagens kurs är aktuell, torsdagens inte.
        assertEquals(true, StockMarketScheduler.isQuoteFromLatestSession(
            "VOLV-B.ST", null, null, stoSeconds("2026-10-02", "17:29"), stoInstant("2026-10-04", "12:00")))
        assertEquals(false, StockMarketScheduler.isQuoteFromLatestSession(
            "VOLV-B.ST", null, null, stoSeconds("2026-10-01", "17:29"), stoInstant("2026-10-04", "12:00")))
        // Krypto alltid aktuell; okänd börs → null.
        assertEquals(true, StockMarketScheduler.isQuoteFromLatestSession("BTC-USD", null, null, 0L, mondayNoon))
        assertNull(StockMarketScheduler.isQuoteFromLatestSession("XYZ", null, null, 0L, mondayNoon))
    }
}
