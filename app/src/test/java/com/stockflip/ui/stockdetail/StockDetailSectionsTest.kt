package com.stockflip.ui.stockdetail

import com.stockflip.InsiderTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDetailSectionsTest {
    @Test
    fun `mäklarsökning tar bort börs- och valutasuffix`() {
        assertEquals("VOLV B", brokerSearchQuery("VOLV-B.ST"))
        assertEquals("BTC", brokerSearchQuery("BTC-USD"))
        assertEquals("AAPL", brokerSearchQuery("aapl"))
        assertEquals("EQNR", brokerSearchQuery("EQNR.OL"))
    }

    private fun tx(type: String = "BUY", shares: Double? = 12000.0, value: Double? = 5_200_000.0) = InsiderTransactionEntity(
        id = "1", symbol = "VOLV-B.ST", cik = "", accessionNumber = "", reportingOwner = "Anna Andersson",
        relationship = "VD", transactionDate = "2026-09-01", shares = shares, pricePerShare = null,
        estimatedValue = value, securityTitle = null, filingDate = null, acceptedAtMillis = null, transactionType = type,
    )

    @Test
    fun `insiderrad visar köp, aktier och värde`() {
        val r = tx().toRowModel()
        assertEquals("Anna Andersson", r.title)
        assertTrue(r.subtitle.startsWith("Köp · "))
        assertTrue(r.subtitle.contains("VD · 2026-09-01"))
        assertTrue(r.value.endsWith("mkr"))
    }

    @Test
    fun `sälj och saknat värde`() {
        val r = tx(type = "SELL", shares = null, value = null).toRowModel()
        assertEquals("Sälj\nVD · 2026-09-01", r.subtitle)
        assertEquals("–", r.value)
    }
}
