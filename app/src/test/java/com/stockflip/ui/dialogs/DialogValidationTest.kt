package com.stockflip.ui.dialogs

import com.stockflip.CurrencyHelper
import com.stockflip.parseDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogValidationTest {

    private fun validate(raw: String?) = validatePositiveDecimal(raw, "tomt", "ogiltigt")

    @Test
    fun `empty or blank input gives the empty message`() {
        assertEquals(DecimalInput.Invalid("tomt"), validate(null))
        assertEquals(DecimalInput.Invalid("tomt"), validate(""))
        assertEquals(DecimalInput.Invalid("tomt"), validate("   "))
    }

    @Test
    fun `non-numeric, zero, negative and non-finite input gives the invalid message`() {
        listOf("abc", "0", "0,00", "-5", "NaN", "Infinity", "12,5,3").forEach { raw ->
            assertEquals("Indata: $raw", DecimalInput.Invalid("ogiltigt"), validate(raw))
        }
    }

    @Test
    fun `comma and dot decimals are accepted`() {
        assertEquals(DecimalInput.Valid(12.5), validate("12,5"))
        assertEquals(DecimalInput.Valid(12.5), validate("12.5"))
        assertEquals(DecimalInput.Valid(250.0), validate(" 250 "))
    }

    @Test
    fun `thousands separated by spaces are accepted`() {
        assertEquals(DecimalInput.Valid(1234.5), validate("1 234,50"))
        assertEquals(DecimalInput.Valid(1234.5), validate("1 234,50"))
        assertEquals(DecimalInput.Valid(1234.5), validate("1 234,50"))
    }

    @Test
    fun `prefilled values from formatDecimal parse back to the same value`() {
        // Förifyllda fält och snabbval använder formatDecimal; värden över 1 000 underkändes tidigare.
        listOf(0.5, 12.34, 999.99, 1234.5, 98765.43, 1_234_567.89).forEach { value ->
            assertEquals(value, CurrencyHelper.formatDecimal(value).parseDecimal()!!, 0.0001)
        }
    }

    @Test
    fun `pair spread treats empty as zero but rejects text`() {
        assertEquals(0.0, parsePairSpread(null)!!, 0.0)
        assertEquals(0.0, parsePairSpread("  ")!!, 0.0)
        assertEquals(2.5, parsePairSpread("2,5")!!, 0.0)
        assertEquals(-1.0, parsePairSpread("-1")!!, 0.0)
        assertNull(parsePairSpread("abc"))
        assertNull(parsePairSpread("NaN"))
    }
}
