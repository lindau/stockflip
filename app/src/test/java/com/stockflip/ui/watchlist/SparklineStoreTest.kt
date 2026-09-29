package com.stockflip.ui.watchlist

import org.junit.Assert.assertEquals
import org.junit.Test

class SparklineStoreTest {
    @Test
    fun `korta serier lämnas orörda`() {
        val v = listOf(1.0, 2.0, 3.0)
        assertEquals(v, SparklineStore.downsample(v, max = 24))
    }

    @Test
    fun `nedsampling behåller första och sista och får rätt antal`() {
        val v = (0 until 100).map { it.toDouble() }
        val d = SparklineStore.downsample(v, max = 10)
        assertEquals(10, d.size)
        assertEquals(0.0, d.first(), 0.0)
        assertEquals(99.0, d.last(), 0.0)
        assertEquals(d.sorted(), d)
    }
}
