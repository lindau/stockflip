package com.stockflip.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineTest {

    @Test
    fun `färre än två punkter ger ingen kurva`() {
        assertTrue(sparklinePoints(emptyList(), 44f, 24f).isEmpty())
        assertTrue(sparklinePoints(listOf(1.0), 44f, 24f).isEmpty())
    }

    @Test
    fun `första och sista punkten ligger på kanterna i x`() {
        val pts = sparklinePoints(listOf(1.0, 2.0, 3.0), 44f, 24f)
        assertEquals(0f, pts.first().x, 0f)
        assertEquals(44f, pts.last().x, 0f)
    }

    @Test
    fun `högre värde ger lägre y`() {
        val pts = sparklinePoints(listOf(1.0, 3.0), 44f, 24f)
        assertTrue(pts[1].y < pts[0].y)
    }

    @Test
    fun `platt serie hamnar mitt i rutan`() {
        val pts = sparklinePoints(listOf(5.0, 5.0, 5.0), 44f, 24f)
        pts.forEach { assertEquals(12f, it.y, 0f) }
    }
}
