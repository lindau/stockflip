package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationRootUiStateTest {

    @Test
    fun `stock root keeps overview visible and clears back stack`() {
        val state = MainActivity().notificationRootUiState(R.id.menu_stocks)

        assertEquals(R.id.menu_stocks, state.menuItemId)
        assertNull(state.rootBackStackTag)
        assertTrue(state.showSwipeRefreshLayout)
        assertTrue(state.showOverviewModeScroll)
        assertFalse(state.showAddPairButton)
    }

    @Test
    fun `pair root hides overview and uses pairs back stack tag`() {
        val state = MainActivity().notificationRootUiState(R.id.menu_pairs)

        assertEquals(R.id.menu_pairs, state.menuItemId)
        assertEquals("pairs", state.rootBackStackTag)
        assertFalse(state.showSwipeRefreshLayout)
        assertFalse(state.showOverviewModeScroll)
        assertTrue(state.showAddPairButton)
    }

    @Test
    fun `alerts root hides overview and uses alerts back stack tag`() {
        val state = MainActivity().notificationRootUiState(R.id.menu_alerts)

        assertEquals(R.id.menu_alerts, state.menuItemId)
        assertEquals("alerts", state.rootBackStackTag)
        assertFalse(state.showSwipeRefreshLayout)
        assertFalse(state.showOverviewModeScroll)
        assertTrue(state.showAddPairButton)
    }
}
