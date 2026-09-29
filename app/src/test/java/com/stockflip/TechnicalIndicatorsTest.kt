package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TechnicalIndicatorsTest {

    // Klassiskt exempel för Wilders RSI(14) (StockCharts "RSI" ChartSchool).
    private val wilderCloses = listOf(
        44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08,
        45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64
    )

    @Test
    fun `rsi matches the known Wilder example`() {
        val rsi = TechnicalIndicators.rsi(wilderCloses, 14)

        assertEquals(wilderCloses.size, rsi.size)
        (0 until 14).forEach { assertNull(rsi[it]) }
        assertEquals(70.53, rsi[14]!!, 0.05)
        assertEquals(66.32, rsi[15]!!, 0.05)
        assertEquals(66.55, rsi[16]!!, 0.05)
        assertEquals(69.41, rsi[17]!!, 0.05)
        assertEquals(66.36, rsi[18]!!, 0.05)
        assertEquals(57.97, rsi[19]!!, 0.05)
    }

    @Test
    fun `rsi of a strictly rising series is 100`() {
        val rsi = TechnicalIndicators.rsi((1..30).map { it.toDouble() }, 14)

        assertEquals(100.0, rsi.last()!!, 0.0001)
    }

    @Test
    fun `rsi of a strictly falling series is 0`() {
        val rsi = TechnicalIndicators.rsi((30 downTo 1).map { it.toDouble() }, 14)

        assertEquals(0.0, rsi.last()!!, 0.0001)
    }

    @Test
    fun `rsi of a flat series is neutral 50`() {
        val rsi = TechnicalIndicators.rsi(List(30) { 10.0 }, 14)

        assertEquals(50.0, rsi.last()!!, 0.0001)
    }

    @Test
    fun `rsi is all null when there are too few closes`() {
        val rsi = TechnicalIndicators.rsi(List(14) { it.toDouble() }, 14)

        assertEquals(14, rsi.size)
        assertEquals(true, rsi.all { it == null })
    }

    @Test
    fun `bollinger bands collapse to the price on a constant series`() {
        val bands = TechnicalIndicators.bollinger(List(25) { 100.0 }, 20, 2.0)

        (0 until 19).forEach { assertNull(bands[it]) }
        val last = bands.last()
        assertNotNull(last)
        assertEquals(100.0, last!!.upper, 0.0001)
        assertEquals(100.0, last.middle, 0.0001)
        assertEquals(100.0, last.lower, 0.0001)
    }

    @Test
    fun `bollinger bands use population standard deviation`() {
        // 2,4,4,4,5,5,7,9 har medelvärde 5 och populationsstandardavvikelse 2.
        val closes = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)

        val bands = TechnicalIndicators.bollinger(closes, period = 8, stdDevs = 2.0)

        val last = bands.last()!!
        assertEquals(5.0, last.middle, 0.0001)
        assertEquals(9.0, last.upper, 0.0001)
        assertEquals(1.0, last.lower, 0.0001)
        (0 until 7).forEach { assertNull(bands[it]) }
    }

    @Test
    fun `bollinger is all null when there are too few closes`() {
        val bands = TechnicalIndicators.bollinger(listOf(1.0, 2.0, 3.0), period = 20)

        assertEquals(true, bands.all { it == null })
    }
}
