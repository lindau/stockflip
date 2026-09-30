package com.stockflip.ui.watchlist

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRowIconsTest {
    private fun stock(id: Int, ticker: String) = WatchItemUiState(
        WatchItem(id = id, watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE), ticker = ticker, companyName = ticker),
        LiveWatchData(currentPrice = 90.0, lastUpdatedAt = 1L),
    )

    private val pair = WatchItemUiState(
        WatchItem(id = 9, watchType = WatchType.PricePair(5.0, false), ticker1 = "A", ticker2 = "B", companyName1 = "A", companyName2 = "B"),
        LiveWatchData(lastUpdatedAt = 1L),
    )

    @Test
    fun `standard ger inga ikoner`() {
        val r = stock(1, "VOLV-B.ST").toRowModel()
        assertFalse(r.hasNote)
        assertFalse(r.hasPodcast)
    }

    @Test
    fun `anteckning matchas exakt på ticker`() {
        val noted = setOf("VOLV-B.ST")
        assertTrue(stock(1, "VOLV-B.ST").toRowModel(notedTickers = noted).hasNote)
        assertFalse(stock(2, "INVE-B.ST").toRowModel(notedTickers = noted).hasNote)
    }

    @Test
    fun `poddomnämnande matchas oberoende av versaler`() {
        val mentioned = setOf("VOLV-B.ST")
        assertTrue(stock(1, "volv-b.st").toRowModel(mentionedTickers = mentioned).hasPodcast)
        assertTrue(stock(1, "VOLV-B.ST").toRowModel(mentionedTickers = mentioned).hasPodcast)
        assertFalse(stock(2, "INVE-B.ST").toRowModel(mentionedTickers = mentioned).hasPodcast)
    }

    @Test
    fun `par får aldrig ikoner`() {
        val r = pair.toRowModel(notedTickers = setOf("A", "B"), mentionedTickers = setOf("A", "B"))
        assertFalse(r.hasNote)
        assertFalse(r.hasPodcast)
    }

    @Test
    fun `sectionsFor för flaggorna vidare`() {
        val s = sectionsFor(
            listOf(stock(1, "VOLV-B.ST"), stock(2, "INVE-B.ST")),
            notedTickers = setOf("INVE-B.ST"),
            mentionedTickers = setOf("VOLV-B.ST"),
        )
        val byId = (s.triggered + s.waiting).associateBy { it.id }
        assertTrue(byId[1]!!.hasPodcast)
        assertTrue(byId[2]!!.hasNote)
        assertFalse(byId[1]!!.hasNote)
        assertFalse(byId[2]!!.hasPodcast)
    }
}
