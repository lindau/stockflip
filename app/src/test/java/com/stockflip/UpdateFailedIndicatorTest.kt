package com.stockflip

import com.stockflip.ui.components.cards.updateFailedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFailedIndicatorTest {

    @Test
    fun `asUpdateFailed keeps last known values and marks them stale`() {
        val previous = LiveWatchData(currentPrice = 250.0, currentDailyChangePercent = 1.5, lastUpdatedAt = 1_000L)

        val failed = previous.asUpdateFailed()

        assertTrue(failed.updateFailed)
        assertEquals(250.0, failed.currentPrice, 0.0001)
        assertEquals(1.5, failed.currentDailyChangePercent!!, 0.0001)
        assertEquals(1_000L, failed.lastUpdatedAt)
    }

    @Test
    fun `asUpdateFailed without previous data gives empty stale data`() {
        val failed = (null as LiveWatchData?).asUpdateFailed()

        assertTrue(failed.updateFailed)
        assertEquals(0.0, failed.currentPrice, 0.0)
        assertEquals(0L, failed.lastUpdatedAt)
    }

    @Test
    fun `label is null when update succeeded`() {
        assertNull(updateFailedLabel(LiveWatchData(currentPrice = 10.0, lastUpdatedAt = 1L)))
    }

    @Test
    fun `label shows time of last known values`() {
        val label = updateFailedLabel(
            LiveWatchData(currentPrice = 10.0, lastUpdatedAt = 1_000L, updateFailed = true),
            formatTime = { "14:32" }
        )

        assertEquals("Kunde inte uppdateras · visar värden från 14:32", label)
    }

    @Test
    fun `label without previous values omits time`() {
        val label = updateFailedLabel(LiveWatchData(updateFailed = true), formatTime = { error("ska inte anropas") })

        assertEquals("Kunde inte uppdateras", label)
    }
}
