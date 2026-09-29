package com.stockflip.ui.watchlist

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import com.stockflip.ui.components.MINUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRowModelTest {

    private fun target(
        price: Double,
        target: Double,
        dir: WatchType.PriceDirection,
        name: String = "Volvo B",
        ticker: String = "VOLV-B.ST",
        id: Int = 1,
        daily: Double? = null,
    ) = WatchItemUiState(
        item = WatchItem(id = id, watchType = WatchType.PriceTarget(target, dir), ticker = ticker, companyName = name),
        live = LiveWatchData(currentPrice = price, currentDailyChangePercent = daily),
    )

    @Test
    fun `väntande prismål visar villkor och hur mycket som återstår`() {
        val row = target(200.0, 245.0, WatchType.PriceDirection.ABOVE).toRowModel()
        assertEquals("Över 245 kr · 22,5 % kvar", row.subtitle)
        assertFalse(row.triggered)
        assertEquals("200,00", row.price)
    }

    @Test
    fun `utlöst prismål markeras och visar inte kvar`() {
        val row = target(248.3, 245.0, WatchType.PriceDirection.ABOVE).toRowModel()
        assertTrue(row.triggered)
        assertEquals("Över 245 kr · utlöst", row.subtitle)
    }

    @Test
    fun `dagsförändring får tecken och färgriktning`() {
        val up = target(100.0, 300.0, WatchType.PriceDirection.ABOVE, daily = 1.2).toRowModel()
        assertEquals("+1,2 %", up.change); assertEquals(true, up.changePositive)
        val down = target(100.0, 300.0, WatchType.PriceDirection.ABOVE, daily = -0.8).toRowModel()
        assertEquals("${MINUS}0,8 %", down.change); assertEquals(false, down.changePositive)
        val flat = target(100.0, 300.0, WatchType.PriceDirection.ABOVE, daily = 0.0).toRowModel()
        assertEquals("0,0 %", flat.change); assertNull(flat.changePositive)
        assertNull(target(100.0, 300.0, WatchType.PriceDirection.ABOVE).toRowModel().change)
    }

    @Test
    fun `saknat pris visas som tankstreck`() {
        val row = target(0.0, 245.0, WatchType.PriceDirection.ABOVE).toRowModel()
        assertEquals("–", row.price)
        assertEquals("Över 245 kr", row.subtitle)
    }

    @Test
    fun `pausad bevakning märks i andraraden`() {
        val paused = target(200.0, 245.0, WatchType.PriceDirection.ABOVE)
            .let { it.copy(item = it.item.copy(isActive = false)) }.toRowModel()
        assertTrue(paused.paused)
        assertTrue(paused.subtitle.endsWith(" · pausad"))
    }

    @Test
    fun `dagsrörelse formuleras efter riktning`() {
        fun sub(d: WatchType.DailyMoveDirection) = WatchItemUiState(
            WatchItem(watchType = WatchType.DailyMove(3.0, d), ticker = "AAPL", companyName = "Apple")
        ).toRowModel().subtitle
        assertEquals("Dagsrörelse ±3 %", sub(WatchType.DailyMoveDirection.BOTH))
        assertEquals("Dagsrörelse över +3 %", sub(WatchType.DailyMoveDirection.UP))
        assertEquals("Dagsrörelse under ${MINUS}3 %", sub(WatchType.DailyMoveDirection.DOWN))
    }

    @Test
    fun `par visas som en rad utan symbol med prisskillnad som kurs`() {
        val pair = WatchItemUiState(
            item = WatchItem(
                watchType = WatchType.PricePair(5.0, false),
                ticker1 = "A", ticker2 = "B", companyName1 = "Alfa", companyName2 = "Beta",
            ),
            live = LiveWatchData(currentPrice1 = 110.0, currentPrice2 = 100.0),
        ).toRowModel()
        assertTrue(pair.isPair)
        assertNull(pair.symbol)
        assertEquals("Alfa – Beta", pair.title)
        assertEquals("10,00", pair.price)
    }

    @Test
    fun `utlösta grupperas överst och sökning filtrerar på namn och symbol`() {
        val items = listOf(
            target(200.0, 245.0, WatchType.PriceDirection.ABOVE, name = "Investor B", ticker = "INVE-B.ST", id = 1),
            target(248.3, 245.0, WatchType.PriceDirection.ABOVE, name = "Volvo B", ticker = "VOLV-B.ST", id = 2),
        )
        val all = sectionsFor(items)
        assertEquals(listOf(2), all.triggered.map { it.id })
        assertEquals(listOf(1), all.waiting.map { it.id })
        assertEquals(listOf(1), sectionsFor(items, "invest").waiting.map { it.id })
        assertEquals(listOf(2), sectionsFor(items, "volv-b").triggered.map { it.id })
        assertTrue(sectionsFor(items, "xyz").isEmpty)
    }
}
