package com.stockflip.ui.stockdetail

import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertGroupsTest {
    private fun alert(id: Int, triggered: Boolean) = WatchItemUiState(
        WatchItem(
            id = id,
            watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE),
            ticker = "VOLV-B.ST",
            isTriggered = triggered,
        )
    )

    @Test
    fun `utlösta delas ut med nyast först och övriga behåller ordningen`() {
        val alerts = listOf(alert(1, false), alert(2, true), alert(3, false), alert(4, true))
        val groups = splitAlertsForDisplay(alerts, mapOf(2 to 1000L, 4 to 2000L))
        assertEquals(listOf(4, 2), groups.triggered.map { it.item.id })
        assertEquals(listOf(1, 3), groups.rest.map { it.item.id })
    }

    @Test
    fun `utan utlösta blir gruppen tom`() {
        val groups = splitAlertsForDisplay(listOf(alert(1, false)), emptyMap())
        assertTrue(groups.triggered.isEmpty())
        assertEquals(listOf(1), groups.rest.map { it.item.id })
    }
}
