package com.stockflip.ui.stockdetail

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import com.stockflip.ui.watchlist.toRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleAndPodcastTest {
    private fun item(live: LiveWatchData) = WatchItemUiState(
        WatchItem(id = 1, watchType = WatchType.PriceTarget(500.0, WatchType.PriceDirection.ABOVE), ticker = "INVE-B.ST", companyName = "Investor"),
        live,
    )

    @Test
    fun `raden markeras när senaste uppdateringen misslyckades`() {
        val stale = item(LiveWatchData(currentPrice = 400.0, lastUpdatedAt = 1_000L, updateFailed = true)).toRowModel()
        assertTrue(stale.staleLabel!!.startsWith("Kunde inte uppdateras"))
        assertNull(item(LiveWatchData(currentPrice = 400.0, lastUpdatedAt = 1_000L)).toRowModel().staleLabel)
    }

    @Test
    fun `utan tidigare värden anges ingen tid`() {
        assertEquals("Kunde inte uppdateras", item(LiveWatchData(updateFailed = true)).toRowModel().staleLabel)
    }

    @Test
    fun `detaljens andra rad visar att värdet är inaktuellt`() {
        val second = alertLines(item(LiveWatchData(currentPrice = 400.0, lastUpdatedAt = 1_000L, updateFailed = true)), null).second
        assertTrue(second!!.startsWith("Kunde inte uppdateras"))
    }

    @Test
    fun `poddtext med och utan synkresultat`() {
        assertEquals("Inga poddomnämnanden har hittats ännu. Synken har inte kört än.", podcastEmptyText(null))
        assertEquals("Inga poddomnämnanden har hittats ännu för denna ticker.\nSenast: ok", podcastEmptyText("Senast: ok"))
    }
}
