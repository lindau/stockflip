package com.stockflip.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TriggerTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun ms(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = utc }.parse(s)!!.time
    private val now = ms("2026-09-29 15:00")

    @Test
    fun `idag med klockslag`() {
        val w = triggerWhen(ms("2026-09-29 09:14"), null, now, utc)!!
        assertEquals("utlöst 09:14", w.listText())
        assertEquals("Utlöst idag 09:14", w.detailText())
    }

    @Test
    fun `igår och äldre`() {
        val y = triggerWhen(ms("2026-09-28 23:50"), null, now, utc)!!
        assertEquals("utlöst igår", y.listText())
        assertEquals("Utlöst igår 23:50", y.detailText())
        val old = triggerWhen(ms("2026-09-12 10:00"), null, now, utc)!!
        assertEquals("utlöst 12 sep", old.listText())
        assertEquals("Utlöst 12 sep", old.detailText())
    }

    @Test
    fun `bara datum ger inget klockslag`() {
        assertEquals("utlöst idag", triggerWhen(null, "2026-09-29", now, utc)!!.listText())
        assertEquals("Utlöst igår", triggerWhen(null, "2026-09-28", now, utc)!!.detailText())
    }

    @Test
    fun `inget att visa`() {
        assertNull(triggerWhen(null, null, now, utc))
        assertNull(triggerWhen(0L, " ", now, utc))
        assertNull(triggerWhen(null, "inte-ett-datum", now, utc))
    }
}
