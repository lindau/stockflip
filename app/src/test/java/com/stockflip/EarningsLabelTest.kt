package com.stockflip

import com.stockflip.ui.components.cards.earningsLabel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class EarningsLabelTest {
    private fun day(y: Int, m: Int, d: Int, hour: Int = 12) =
        Calendar.getInstance().apply { clear(); set(y, m, d, hour, 0, 0) }.timeInMillis

    private val now = day(2026, Calendar.OCTOBER, 11, 15)

    @Test
    fun countsDays() {
        assertTrue(earningsLabel(day(2026, Calendar.OCTOBER, 23), false, now)!!.startsWith("Rapport om 12 dagar · 23 "))
    }

    @Test
    fun todayAndTomorrow() {
        assertTrue(earningsLabel(day(2026, Calendar.OCTOBER, 11, 8), false, now)!!.startsWith("Rapport idag · 11 "))
        assertTrue(earningsLabel(day(2026, Calendar.OCTOBER, 12), true, now)!!.startsWith("Bokslut i morgon · 12 "))
    }

    @Test
    fun hidesMissingOrPast() {
        assertNull(earningsLabel(0L, false, now))
        assertNull(earningsLabel(day(2026, Calendar.OCTOBER, 1), false, now))
    }
}
