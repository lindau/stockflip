package com.stockflip.ui.watchlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSortTest {
    @Test
    fun `standardsortering är skapad stigande`() {
        assertTrue(WatchSort().isDefault)
        assertFalse(WatchSort(WatchSortKey.CREATED, descending = true).isDefault)
        assertFalse(WatchSort(WatchSortKey.NAME).isDefault)
    }

    @Test
    fun `tryck på vald sortering vänder riktningen`() {
        val sort = WatchSort(WatchSortKey.NAME)
        assertEquals(WatchSort(WatchSortKey.NAME, descending = true), sort.tapped(WatchSortKey.NAME))
        assertEquals(sort, sort.tapped(WatchSortKey.NAME).tapped(WatchSortKey.NAME))
    }

    @Test
    fun `tryck på annan sortering ger dess standardriktning`() {
        val sort = WatchSort(WatchSortKey.NAME, descending = true)
        assertEquals(WatchSort(WatchSortKey.CHANGE, descending = true), sort.tapped(WatchSortKey.CHANGE))
        assertEquals(WatchSort(WatchSortKey.PROXIMITY, descending = false), sort.tapped(WatchSortKey.PROXIMITY))
        assertEquals(WatchSort(WatchSortKey.NAME, descending = false), WatchSort(WatchSortKey.CHANGE).tapped(WatchSortKey.NAME))
    }

    @Test
    fun `sparade värden läses nya och gamla`() {
        assertEquals(WatchSort(WatchSortKey.NAME, true), WatchSort.fromPrefs("NAME", true))
        assertEquals(WatchSort(WatchSortKey.CHANGE, true), WatchSort.fromPrefs("CHANGE_DESC", false))
        assertEquals(WatchSort(WatchSortKey.CHANGE, false), WatchSort.fromPrefs("CHANGE_ASC", true))
        assertEquals(WatchSort(WatchSortKey.PROXIMITY, false), WatchSort.fromPrefs("NEAREST", true))
        assertEquals(WatchSort(WatchSortKey.NAME, false), WatchSort.fromPrefs("NAME", false))
        assertEquals(WatchSort(), WatchSort.fromPrefs("PRICE_DESC", true))
        assertEquals(WatchSort(), WatchSort.fromPrefs(null, false))
    }
}
