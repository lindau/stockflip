package com.stockflip.ui.pairdetail

import com.stockflip.PairDetailData
import com.stockflip.StockSummary
import com.stockflip.WatchItem
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairDetailModelTest {
    private fun stock(sym: String, name: String, price: Double?) = StockSummary(sym, name, price, 1.0, "SEK")
    private fun data(type: WatchType, spread: Double?, item: WatchItem.() -> WatchItem = { this }) = PairDetailData(
        watchItem = WatchItem(watchType = type, ticker1 = "A", ticker2 = "B").item(),
        stockA = stock("A", "Aktie A", 100.0), stockB = stock("B", "Aktie B", 95.0), spread = spread,
    )

    @Test
    fun `aktiv väntar med kvar-värde`() {
        val m = data(WatchType.PricePair(10.0, false), 5.0).toModel()!!
        assertEquals(PairStatus.Active, m.status)
        assertEquals("Aktie A / Aktie B", m.title)
        assertEquals("≥ ${com.stockflip.CurrencyHelper.formatDecimal(10.0)}", m.trigger)
        assertEquals(com.stockflip.CurrencyHelper.formatDecimal(5.0), m.remaining)
    }

    @Test
    fun `utlöst när skillnaden når gränsen`() {
        assertEquals(PairStatus.Triggered, data(WatchType.PricePair(10.0, false), -12.0).toModel()!!.status)
    }

    @Test
    fun `lika pris utan gräns och okänd spread`() {
        val m = data(WatchType.PricePair(0.0, true), null).toModel()!!
        assertEquals("–", m.spread)
        assertEquals("–", m.remaining)
        assertEquals("När kurserna möts", m.trigger)
    }

    @Test
    fun `pausad och icke-par`() {
        assertEquals(PairStatus.Inactive, data(WatchType.PricePair(10.0, false), 1.0) { copy(isActive = false) }.toModel()!!.status)
        assertNull(data(WatchType.InsiderBuy(), null).toModel())
    }
}
