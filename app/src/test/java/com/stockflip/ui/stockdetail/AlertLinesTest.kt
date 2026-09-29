package com.stockflip.ui.stockdetail

import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AlertLinesTest {
    @Test
    fun `hero med belopp och procent, eller bara ett av dem`() {
        assertEquals("+2,95 (+1,2 %)", heroChangeText(2.95, 1.2))
        assertEquals("−1,00 (−0,4 %)", heroChangeText(-1.0, -0.4))
        assertEquals("+1,2 %", heroChangeText(null, 1.2))
        assertEquals("+3,00", heroChangeText(3.0, null))
        assertNull(heroChangeText(null, null))
    }

    private fun item(type: WatchType, live: LiveWatchData = LiveWatchData(lastUpdatedAt = 1L), triggered: Boolean = false, active: Boolean = true, date: String? = null) =
        WatchItemUiState(WatchItem(id = 1, watchType = type, ticker = "VOLV-B.ST", companyName = "Volvo B",
            isTriggered = triggered, isActive = active, lastTriggeredDate = date), live)

    @Test
    fun `utlöst visar tid, väntande visar nuvärde`() {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val now = fmt.parse("2026-09-29 15:00")!!.time
        val at = fmt.parse("2026-09-29 09:14")!!.time
        val hit = item(WatchType.PriceTarget(245.0, WatchType.PriceDirection.ABOVE), triggered = true, active = false, date = "2026-09-29")
        assertEquals("Utlöst idag 09:14", alertLines(hit, at, now).second)

        val drawdown = item(WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 15.0, WatchType.HighReference.FIFTY_TWO_WEEK_HIGH),
            LiveWatchData(currentDropPercentage = 14.3, lastUpdatedAt = 1L))
        assertEquals("Nu −14,3 %", alertLines(drawdown, null, now).second)

        val metric = item(WatchType.KeyMetrics(WatchType.MetricType.PE_RATIO, 22.0, WatchType.PriceDirection.BELOW),
            LiveWatchData(currentMetricValue = 24.1, lastUpdatedAt = 1L))
        assertEquals("Nu 24,1", alertLines(metric, null, now).second)
    }

    @Test
    fun `pausad och utan värde`() {
        val paused = item(WatchType.DailyMove(3.0, WatchType.DailyMoveDirection.BOTH), active = false)
        assertEquals("Pausad", alertLines(paused, null).second)
        assertNull(alertLines(item(WatchType.InsiderBuy()), null).second)
        assertTrue(alertLines(paused, null).first.startsWith("Dagsrörelse"))
    }
}
