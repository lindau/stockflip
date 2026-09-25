package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexSearchParsingTest {

    @Test
    fun `parseIndexSearchResults keeps only INDEX quotes with caret symbol`() {
        val body = """
            {"quotes":[
              {"symbol":"^OMXS30","shortname":"OMX Stockholm 30 Index","quoteType":"INDEX"},
              {"symbol":"^GSPC","longname":"S&P 500","quoteType":"INDEX"},
              {"symbol":"XACT-OMXS30.ST","shortname":"XACT OMXS30","quoteType":"ETF"},
              {"symbol":"VOLV-B.ST","shortname":"Volvo B","quoteType":"EQUITY"}
            ]}
        """.trimIndent()

        val results = YahooFinanceService.parseIndexSearchResults(body)

        assertEquals(listOf("^OMXS30", "^GSPC"), results.map { it.symbol })
        assertTrue(results.all { it.isIndex })
        assertTrue(results[0].isSwedish)
        assertEquals("S&P 500", results[1].name)
    }
}
