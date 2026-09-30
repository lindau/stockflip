package com.stockflip.ui.watchlist

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSectionsTest {

    private fun target(id: Int, ticker: String, name: String, price: Double, targetPrice: Double) = WatchItemUiState(
        item = WatchItem(id = id, watchType = WatchType.PriceTarget(targetPrice, WatchType.PriceDirection.ABOVE), ticker = ticker, companyName = name),
        live = LiveWatchData(currentPrice = price),
    )

    private fun pair() = WatchItemUiState(
        item = WatchItem(id = 9, watchType = WatchType.PricePair(10.0, false), ticker1 = "A.ST", ticker2 = "B.ST", companyName1 = "A", companyName2 = "B"),
        live = LiveWatchData(currentPrice1 = 100.0, currentPrice2 = 90.0),
    )

    @Test
    fun `flera bevakningar på samma aktie ger en rad med antal och utlösta`() {
        val s = stockSectionsFor(listOf(
            target(1, "VOLV-B.ST", "Volvo B", 250.0, 245.0),
            target(2, "VOLV-B.ST", "Volvo B", 250.0, 300.0),
            target(3, "ERIC-B.ST", "Ericsson B", 80.0, 100.0),
        ))
        assertEquals(2, s.stocks.size)
        assertEquals("Volvo B", s.stocks[0].title)
        assertEquals("2 bevakningar · 1 utlöst", s.stocks[0].subtitle)
        assertTrue(s.stocks[0].triggered)
        assertEquals("1 bevakning", s.stocks[1].subtitle)
    }

    @Test
    fun `aktiepar listas separat och sökning filtrerar båda`() {
        val items = listOf(target(1, "VOLV-B.ST", "Volvo B", 250.0, 300.0), pair())
        val all = stockSectionsFor(items)
        assertEquals(1, all.stocks.size); assertEquals(1, all.pairs.size)
        val q = stockSectionsFor(items, query = "volvo")
        assertEquals(1, q.stocks.size); assertTrue(q.pairs.isEmpty())
    }
}
