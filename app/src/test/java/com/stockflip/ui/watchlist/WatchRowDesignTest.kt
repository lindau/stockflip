package com.stockflip.ui.watchlist

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WatchRowDesignTest {
    private fun target(ticker: String, level: Double, price: Double, triggered: Boolean = false, date: String? = null) = WatchItemUiState(
        WatchItem(id = 1, watchType = WatchType.PriceTarget(level, WatchType.PriceDirection.ABOVE), ticker = ticker,
            companyName = "X", isTriggered = triggered, lastTriggeredDate = date),
        LiveWatchData(currentPrice = price, lastUpdatedAt = 1L),
    )

    @Test
    fun `dollar och euro visas med rätt enhet, svenska aktier med kr`() {
        assertTrue(target("BTC-USD", 100000.0, 97420.0).toRowModel().subtitle.startsWith("Över 100"))
        assertTrue(target("BTC-USD", 100000.0, 97420.0).toRowModel().subtitle.contains(" \$"))
        assertTrue(target("VOLV-B.ST", 245.0, 244.0).toRowModel().subtitle.startsWith("Över 245 kr"))
        assertEquals("€", currencyUnit("ETH-EUR"))
        assertEquals("kr", currencyUnit(null))
    }

    @Test
    fun `utlöst rad visar klockslag när historik finns, annars datum`() {
        val zone = TimeZone.getDefault()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }
        val now = fmt.parse("2026-09-29 15:00")!!.time
        val at = fmt.parse("2026-09-29 09:14")!!.time
        val item = target("VOLV-B.ST", 245.0, 250.0, triggered = true, date = "2026-09-29")
        assertTrue(item.toRowModel(at, now).subtitle.endsWith("utlöst 09:14"))
        assertTrue(item.toRowModel(null, now).subtitle.endsWith("utlöst idag"))
        assertTrue(sectionsFor(listOf(item), "", mapOf(1 to at), now).triggered.single().subtitle.endsWith("utlöst 09:14"))
    }
}
