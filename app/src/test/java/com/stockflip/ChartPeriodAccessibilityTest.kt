package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPeriodAccessibilityTest {

    @Test
    fun `every period has a spoken label that differs from its short label`() {
        ChartPeriod.entries.forEach { period ->
            val spoken = period.accessibilityLabel()
            assertTrue("$period saknar uppläsningstext", spoken.isNotBlank())
            assertTrue("$period läses som förkortning: $spoken", spoken != period.label)
        }
    }

    @Test
    fun `spoken labels are unique and in Swedish`() {
        val labels = ChartPeriod.entries.map { it.accessibilityLabel() }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals("1 dag", ChartPeriod.DAY.accessibilityLabel())
        assertEquals("1 år", ChartPeriod.YEAR.accessibilityLabel())
        assertEquals("5 år", ChartPeriod.FIVE_YEARS.accessibilityLabel())
    }
}
