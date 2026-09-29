package com.stockflip

import com.stockflip.ui.DetailSectionSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailSectionSummariesTest {
    @Test
    fun alerts() {
        assertNull(DetailSectionSummaries.alerts(0, 0))
        assertEquals("1 aktiv", DetailSectionSummaries.alerts(1, 0))
        assertEquals("4 aktiva · 1 triggad", DetailSectionSummaries.alerts(4, 1))
        assertEquals("2 triggade", DetailSectionSummaries.alerts(0, 2))
    }

    @Test
    fun insider() {
        assertNull(DetailSectionSummaries.insider(0, 0))
        assertEquals("3 köp · 1 sälj", DetailSectionSummaries.insider(3, 1))
    }

    @Test
    fun podcast() {
        assertEquals("Av", DetailSectionSummaries.podcast(false, 3))
        assertNull(DetailSectionSummaries.podcast(true, 0))
        assertEquals("1 omnämnande", DetailSectionSummaries.podcast(true, 1))
        assertEquals("2 omnämnanden", DetailSectionSummaries.podcast(true, 2))
    }

    @Test
    fun note() {
        assertNull(DetailSectionSummaries.note(null))
        assertEquals("Rad två", DetailSectionSummaries.note("\n  Rad två \nrad tre"))
    }
}
