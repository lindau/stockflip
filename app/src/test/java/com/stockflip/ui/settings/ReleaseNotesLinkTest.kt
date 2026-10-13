package com.stockflip.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesLinkTest {
    @Test
    fun `finds GitHub changelog link and strips markdown`() {
        val text = "**Full Changelog**: https://github.com/lindau/StockFlip/compare/v1.3.3...v1.3.4"
        val link = findLinks(text).single()
        assertEquals("https://github.com/lindau/StockFlip/compare/v1.3.3...v1.3.4", link.url)
        assertEquals(text.length, link.end)
        assertEquals(link.url, text.substring(link.start, link.end))
    }

    @Test
    fun `trailing punctuation and closing parenthesis are not part of the link`() {
        assertEquals("https://a.se/x", findLinks("Se (https://a.se/x).").single().url)
        assertEquals("https://a.se/x", findLinks("Se https://a.se/x, tack").single().url)
        assertEquals("https://a.se/x", findLinks("**https://a.se/x**").single().url)
    }

    @Test
    fun `finds several links`() {
        assertEquals(2, findLinks("https://a.se/1 och https://b.se/2").size)
    }

    @Test
    fun `plain http and text without links give no links`() {
        assertTrue(findLinks("http://a.se/x").isEmpty())
        assertTrue(findLinks("Inga länkar här").isEmpty())
        assertTrue(findLinks("https://").isEmpty())
    }
}
