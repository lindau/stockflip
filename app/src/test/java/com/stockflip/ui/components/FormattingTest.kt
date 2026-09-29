package com.stockflip.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `formatNumber använder decimalkomma`() {
        assertEquals("248,30", formatNumber(248.3))
        assertEquals("0", formatNumber(0.4, decimals = 0))
    }

    @Test
    fun `formatNumber ger minustecken för negativa värden`() {
        assertEquals("${MINUS}5,00", formatNumber(-5.0))
    }

    @Test
    fun `signed percent visar alltid tecken`() {
        assertEquals("+1,2 %", formatSignedPercent(1.2))
        assertEquals("${MINUS}0,8 %", formatSignedPercent(-0.8))
    }

    @Test
    fun `signed percent visar inget tecken för noll efter avrundning`() {
        assertEquals("0,0 %", formatSignedPercent(0.0))
        assertEquals("0,0 %", formatSignedPercent(-0.02))
    }

    @Test
    fun `signed amount visar tecken`() {
        assertEquals("+2,95", formatSignedAmount(2.95))
        assertEquals("${MINUS}2,95", formatSignedAmount(-2.95))
    }

    @Test
    fun `rangeFraction placerar värdet i intervallet`() {
        assertEquals(0.5f, rangeFraction(50.0, 0.0, 100.0), 0.0001f)
        assertEquals(0f, rangeFraction(-10.0, 0.0, 100.0), 0f)
        assertEquals(1f, rangeFraction(150.0, 0.0, 100.0), 0f)
        assertEquals(0f, rangeFraction(5.0, 10.0, 10.0), 0f)
    }
}
