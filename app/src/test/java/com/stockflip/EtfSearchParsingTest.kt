package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtfSearchParsingTest {

    @Test
    fun `parseEtfSearchResults keeps only ETF quotes and skips funds, equities and indices`() {
        val body = """
            {"quotes":[
              {"symbol":"XACT-OMXS30.ST","shortname":"XACT OMXS30","exchange":"STO","quoteType":"ETF"},
              {"symbol":"SPY","shortname":"SPDR S&P 500","exchange":"PCX","quoteType":"ETF"},
              {"symbol":"VFIAX","shortname":"Vanguard 500 Index Admiral","quoteType":"MUTUALFUND"},
              {"symbol":"VOLV-B.ST","shortname":"Volvo B","quoteType":"EQUITY"},
              {"symbol":"^OMXS30","shortname":"OMX Stockholm 30","quoteType":"INDEX"}
            ]}
        """.trimIndent()

        val results = YahooFinanceService.parseEtfSearchResults(body)

        assertEquals(listOf("XACT-OMXS30.ST", "SPY"), results.map { it.symbol })
        assertTrue(results.all { it.isEtf && !it.isIndex && !it.isCrypto })
        assertTrue(results[0].isSwedish)
        assertEquals("XACT OMXS30", results[0].name)
        assertEquals("SPDR S&P 500 (PCX)", results[1].name)
    }
}
