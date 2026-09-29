package com.stockflip.ui.components.cards

import com.stockflip.SmaPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AlignSeriesToChartTest {

    @Test
    fun `carries the latest point at or before each chart timestamp forward`() {
        val points = listOf(SmaPoint(10, 1.0), SmaPoint(20, 2.0), SmaPoint(30, 3.0))

        val aligned = alignSeriesToChart(listOf(5L, 10L, 15L, 20L, 35L), points) { it.timestamp }

        assertEquals(listOf(null, points[0], points[0], points[1], points[2]), aligned)
    }

    @Test
    fun `returns nulls for empty series`() {
        val aligned = alignSeriesToChart(listOf(1L, 2L), emptyList<SmaPoint>()) { it.timestamp }

        assertEquals(listOf(null, null), aligned)
    }

    @Test
    fun `returns empty list for empty chart`() {
        val aligned = alignSeriesToChart(emptyList(), listOf(SmaPoint(1, 1.0))) { it.timestamp }

        assertEquals(emptyList<SmaPoint?>(), aligned)
    }
}
