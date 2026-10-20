package com.stockflip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEarningsParserTest {

    private val now = 1_800_000_000_000L
    private val nowSec = now / 1000L

    private fun parse(json: String) = parseNextEarnings(JSONObject(json), now)

    @Test
    fun `reads date from calendarEvents`() {
        val info = parse("""{"calendarEvents":{"earnings":{"earningsDate":[{"raw":${nowSec + 86400}}]}}}""")
        assertEquals((nowSec + 86400) * 1000L, info!!.reportDateMillis)
        assertFalse(info.isAnnualReport)
    }

    @Test
    fun `falls back to earningsChart and detects annual report`() {
        val info = parse("""{"earnings":{"earningsChart":{"currentQuarterEstimate":"4q","earningsDate":[{"raw":${nowSec + 86400}}]}}}""")
        assertEquals((nowSec + 86400) * 1000L, info!!.reportDateMillis)
        assertTrue(info.isAnnualReport)
    }

    @Test
    fun `picks earliest upcoming date and skips past ones`() {
        val info = parse("""{"calendarEvents":{"earnings":{"earningsDate":[{"raw":${nowSec - 5 * 86400}},{"raw":${nowSec + 3 * 86400}},{"raw":${nowSec + 2 * 86400}}]}}}""")
        assertEquals((nowSec + 2 * 86400) * 1000L, info!!.reportDateMillis)
    }

    @Test
    fun `returns null without any date`() {
        assertNull(parse("""{"calendarEvents":{"earnings":{}},"earnings":{"earningsChart":{}}}"""))
        assertNull(parse("""{}"""))
        assertNull(parseNextEarnings(null, now))
    }

    @Test
    fun `returns null when only past dates exist`() {
        assertNull(parse("""{"calendarEvents":{"earnings":{"earningsDate":[{"raw":${nowSec - 10 * 86400}}]}}}"""))
    }
}
