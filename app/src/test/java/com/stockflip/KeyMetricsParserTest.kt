package com.stockflip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMetricsParserTest {

    private fun result(json: String): JSONObject = JSONObject(json)

    @Test
    fun `parses price to book, ev to ebitda and debt to equity`() {
        val metrics = parseKeyMetrics(result("""
            {
              "summaryDetail": { "trailingPE": { "raw": 12.5 } },
              "defaultKeyStatistics": {
                "priceToBook": { "raw": 3.4 },
                "enterpriseToEbitda": { "raw": 9.8 }
              },
              "financialData": { "debtToEquity": { "raw": 45.2 } }
            }
        """.trimIndent()))

        assertNotNull(metrics)
        assertEquals(3.4, metrics!!.priceToBook!!, 0.0001)
        assertEquals(9.8, metrics.evToEbitda!!, 0.0001)
        assertEquals(45.2, metrics.debtToEquity!!, 0.0001)
        assertEquals(12.5, metrics.peRatio!!, 0.0001)
    }

    @Test
    fun `keeps negative values for the new metrics`() {
        val metrics = parseKeyMetrics(result("""
            {
              "defaultKeyStatistics": {
                "priceToBook": { "raw": -2.1 },
                "enterpriseToEbitda": { "raw": -15.0 }
              },
              "financialData": { "debtToEquity": { "raw": -80.0 } }
            }
        """.trimIndent()))!!

        assertEquals(-2.1, metrics.priceToBook!!, 0.0001)
        assertEquals(-15.0, metrics.evToEbitda!!, 0.0001)
        assertEquals(-80.0, metrics.debtToEquity!!, 0.0001)
    }

    @Test
    fun `missing or empty fields give null values`() {
        val metrics = parseKeyMetrics(result("""
            {
              "defaultKeyStatistics": { "priceToBook": {} },
              "financialData": { "currentPrice": { "raw": 100.0 } }
            }
        """.trimIndent()))!!

        assertNull(metrics.priceToBook)
        assertNull(metrics.evToEbitda)
        assertNull(metrics.debtToEquity)
    }

    @Test
    fun `keeps existing metrics behaviour`() {
        val metrics = parseKeyMetrics(result("""
            {
              "summaryDetail": {
                "trailingAnnualDividendYield": { "raw": 0.031 },
                "priceToSalesTrailing12Months": { "raw": 1.5 },
                "marketCap": { "raw": 1000000.0 }
              },
              "defaultKeyStatistics": { "forwardEps": { "raw": 7.0 } },
              "financialData": { "returnOnEquity": { "raw": 0.18 } }
            }
        """.trimIndent()))!!

        assertEquals(3.1, metrics.dividendYield!!, 0.0001)
        assertEquals(1.5, metrics.psRatio!!, 0.0001)
        assertEquals(1000000.0, metrics.marketCap!!, 0.0001)
        assertEquals(7.0, metrics.earningsPerShare!!, 0.0001)
        assertEquals(18.0, metrics.returnOnEquity!!, 0.0001)
    }

    @Test
    fun `returns null when no module is present`() {
        assertNull(parseKeyMetrics(result("{}")))
        assertNull(parseKeyMetrics(null))
    }

    @Test
    fun `parses analyst target fields`() {
        val metrics = parseKeyMetrics(result("""
            {
              "financialData": {
                "targetMeanPrice": { "raw": 250.5 },
                "targetHighPrice": { "raw": 320.0 },
                "targetLowPrice": { "raw": 180.0 },
                "numberOfAnalystOpinions": { "raw": 24 },
                "recommendationKey": "Strong_Buy",
                "financialCurrency": "SEK"
              }
            }
        """.trimIndent()))!!

        assertEquals(250.5, metrics.targetMeanPrice!!, 0.0001)
        assertEquals(320.0, metrics.targetHighPrice!!, 0.0001)
        assertEquals(180.0, metrics.targetLowPrice!!, 0.0001)
        assertEquals(24, metrics.analystCount)
        assertEquals("strong_buy", metrics.recommendationKey)
        assertEquals("SEK", metrics.financialCurrency)
    }

    @Test
    fun `none recommendation, zero analysts and blank currency become null`() {
        val metrics = parseKeyMetrics(result("""
            {
              "financialData": {
                "targetMeanPrice": { "raw": 0 },
                "numberOfAnalystOpinions": { "raw": 0 },
                "recommendationKey": "none",
                "financialCurrency": "  "
              }
            }
        """.trimIndent()))!!

        assertNull(metrics.targetMeanPrice)
        assertNull(metrics.analystCount)
        assertNull(metrics.recommendationKey)
        assertNull(metrics.financialCurrency)
    }

    @Test
    fun `missing analyst fields give null`() {
        val metrics = parseKeyMetrics(result("""{ "financialData": { "debtToEquity": { "raw": 10.0 } } }"""))!!

        assertNull(metrics.targetMeanPrice)
        assertNull(metrics.targetHighPrice)
        assertNull(metrics.targetLowPrice)
        assertNull(metrics.analystCount)
        assertNull(metrics.recommendationKey)
    }
}
