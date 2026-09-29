package com.stockflip.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {

    @Test
    fun `flikarnas rotskärmar visar nedre navigering`() {
        assertTrue(isTopLevelRoute(Routes.WATCHLIST))
        assertTrue(isTopLevelRoute(Routes.MARKET))
        assertTrue(isTopLevelRoute(Routes.SETTINGS))
    }

    @Test
    fun `detaljsidor och okänd rutt döljer nedre navigering`() {
        assertFalse(isTopLevelRoute(Routes.STOCK_DETAIL))
        assertFalse(isTopLevelRoute(null))
    }

    @Test
    fun `det finns exakt tre flikar i rätt ordning`() {
        assertEquals(
            listOf(Routes.WATCHLIST, Routes.MARKET, Routes.SETTINGS),
            TopLevelTab.entries.map { it.route },
        )
    }
}
