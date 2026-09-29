package com.stockflip.ui.watchlist

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastUpdatedTest {
    private fun item(id: Int, at: Long, failed: Boolean = false) = WatchItemUiState(
        WatchItem(id = id, watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE), ticker = "A"),
        LiveWatchData(currentPrice = 1.0, lastUpdatedAt = at, updateFailed = failed),
    )

    @Test
    fun `senaste lyckade uppdatering används`() {
        val label = lastUpdatedLabel(listOf(item(1, 1_000L), item(2, 5_000L), item(3, 9_000L, failed = true))) { "t=$it" }
        assertEquals("Uppdaterad t=5000", label)
    }

    @Test
    fun `ingen uppdatering ger null`() {
        assertNull(lastUpdatedLabel(emptyList()) { "x" })
        assertNull(lastUpdatedLabel(listOf(item(1, 0L))) { "x" })
    }
}
