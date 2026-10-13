package com.stockflip.ui.stockdetail

import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggeredCardsTest {
    private fun alert(id: Int, triggered: Boolean) = WatchItemUiState(
        WatchItem(
            id = id,
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
            ticker = "VOLV-B.ST",
            isTriggered = triggered,
        )
    )

    private val alerts = listOf(alert(1, false), alert(2, true), alert(3, true), alert(4, true))
    private val times = mapOf(2 to 1000L, 3 to 3000L, 4 to 2000L)

    private fun cards(dismissed: Set<Int> = emptySet(), notifId: Int? = null, title: String? = null, message: String? = null) =
        triggeredCardsFor(alerts, times, dismissed, notifId, title, message, now = 5000L)

    @Test
    fun `nyast först med position och total`() {
        val c = cards()
        assertEquals(listOf(3, 4, 2), c.map { it.alert.item.id })
        assertEquals(listOf(1, 2, 3), c.map { it.position })
        assertTrue(c.all { it.total == 3 })
    }

    @Test
    fun `stängda filtreras bort och räknaren minskar`() {
        val c = cards(dismissed = setOf(3))
        assertEquals(listOf(4, 2), c.map { it.alert.item.id })
        assertEquals(2, c.first().total)
    }

    @Test
    fun `alla stängda ger tom kö`() {
        assertTrue(cards(dismissed = setOf(2, 3, 4)).isEmpty())
    }

    @Test
    fun `notisens bevakning först och med notisens text`() {
        val c = cards(notifId = 2, title = "Titel", message = "Text")
        assertEquals(listOf(2, 3, 4), c.map { it.alert.item.id })
        assertEquals("Titel", c.first().title)
        assertEquals("Text", c.first().message)
        assertTrue(c[1].title != "Titel")
    }

    @Test
    fun `ej längre utlöst notisbevakning ger ingen kort`() {
        val c = triggeredCardsFor(listOf(alert(1, false)), emptyMap(), emptySet(), 1, "T", "M")
        assertTrue(c.isEmpty())
    }
}
