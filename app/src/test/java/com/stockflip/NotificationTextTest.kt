package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationTextTest {
    @Test
    fun `målpris under, svensk aktie`() {
        val t = NotificationText.priceTarget("Volvo B", above = false, target = 245.0, price = 244.8, currency = "SEK")
        assertEquals("Volvo B under 245 kr", t.title)
        assertEquals("Kursen är ${CurrencyHelper.formatDecimal(244.8)} kr", t.message)
    }

    @Test
    fun `målpris över, utländsk valuta och decimalnivå`() {
        val t = NotificationText.priceTarget("Apple", above = true, target = 190.5, price = 191.2, currency = "USD")
        assertEquals("Apple över ${CurrencyHelper.formatDecimal(190.5)} USD", t.title)
    }

    @Test
    fun `saknad kurs ger reservtext och inga utropstecken`() {
        val t = NotificationText.priceTarget("Volvo B", above = true, target = 250.0, price = null, currency = null)
        assertEquals("Målpriset 250 kr är nått", t.message)
        assertFalse((t.title + t.message).contains("!"))
    }
}
