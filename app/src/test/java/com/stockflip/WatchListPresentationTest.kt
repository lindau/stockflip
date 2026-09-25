package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchListPresentationTest {

    private val today = "2026-09-25"

    private fun uiState(watchType: WatchType, isActive: Boolean = true, isTriggered: Boolean = false, lastTriggeredDate: String? = null) =
        WatchItemUiState(
            WatchItem(
                watchType = watchType,
                ticker = "VOLV-B.ST",
                isActive = isActive,
                isTriggered = isTriggered,
                lastTriggeredDate = lastTriggeredDate
            )
        )

    private val priceTarget = uiState(WatchType.PriceTarget(250.0, WatchType.PriceDirection.ABOVE))
    private val ath = uiState(WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 10.0))
    private val dailyMove = uiState(WatchType.DailyMove(3.0, WatchType.DailyMoveDirection.BOTH))
    private val priceRange = uiState(WatchType.PriceRange(100.0, 200.0))
    private val keyMetric = uiState(WatchType.KeyMetrics(WatchType.MetricType.PE_RATIO, 15.0, WatchType.PriceDirection.BELOW))
    private val pair = uiState(WatchType.PricePair(5.0, false))
    private val combined = uiState(WatchType.Combined(AlertExpression.Single(AlertRule.SinglePrice("VOLV-B.ST", AlertRule.PriceComparisonType.ABOVE, 250.0))))
    private val paused = uiState(WatchType.PriceTarget(250.0, WatchType.PriceDirection.ABOVE), isActive = false)
    private val triggered = uiState(WatchType.PriceTarget(250.0, WatchType.PriceDirection.ABOVE), isTriggered = true, lastTriggeredDate = "2026-09-20")

    private val all = listOf(priceTarget, ath, dailyMove, priceRange, keyMetric, pair, combined, paused, triggered)

    @Test
    fun `ALL matches every item`() {
        assertEquals(all, all.filter { AlertsFilter.ALL.matches(it) })
    }

    @Test
    fun `PRICE matches price-based types only`() {
        val matched = all.filter { AlertsFilter.PRICE.matches(it) }
        assertEquals(listOf(priceTarget, ath, dailyMove, priceRange, paused, triggered), matched)
        assertFalse(AlertsFilter.PRICE.matches(keyMetric))
        assertFalse(AlertsFilter.PRICE.matches(pair))
        assertFalse(AlertsFilter.PRICE.matches(combined))
    }

    @Test
    fun `METRICS and PAIRS match their own type`() {
        assertEquals(listOf(keyMetric), all.filter { AlertsFilter.METRICS.matches(it) })
        assertEquals(listOf(pair), all.filter { AlertsFilter.PAIRS.matches(it) })
    }

    @Test
    fun `ACTIVE excludes paused items and TRIGGERED shows triggered ones`() {
        assertFalse(AlertsFilter.ACTIVE.matches(paused))
        assertTrue(AlertsFilter.ACTIVE.matches(priceTarget))
        assertEquals(listOf(triggered), all.filter { AlertsFilter.TRIGGERED.matches(it) })
    }

    @Test
    fun `each filter has its own empty text`() {
        val titles = AlertsFilter.entries.map { it.emptyTitleRes }
        val subtitles = AlertsFilter.entries.map { it.emptySubtitleRes }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals(subtitles.size, subtitles.toSet().size)
        assertEquals(R.string.alerts_empty_title, AlertsFilter.ALL.emptyTitleRes)
    }

    @Test
    fun `header counts active items and triggers from today only`() {
        val triggeredToday = uiState(
            WatchType.PriceTarget(250.0, WatchType.PriceDirection.ABOVE),
            isTriggered = true,
            lastTriggeredDate = today
        )
        val items = all + triggeredToday
        val summary = alertsHeaderSummary(items, today)
        assertEquals(1, summary.triggeredToday)
        assertEquals(items.size - 1, summary.active) // bara "paused" är inaktiv
    }

    @Test
    fun `header summary of empty list is zero`() {
        assertEquals(AlertsHeaderSummary(0, 0), alertsHeaderSummary(emptyList(), today))
    }

    @Test
    fun `empty state is hidden when rows are visible even after an earlier error`() {
        assertEquals(WatchListEmptyState.Hidden, watchListEmptyState(visibleItemCount = 2, loadError = null))
        assertEquals(WatchListEmptyState.Hidden, watchListEmptyState(visibleItemCount = 1, loadError = "fel"))
    }

    @Test
    fun `load error wins over no-items text so filter changes cannot hide it`() {
        // Samma anrop som vid filterbyte: fortfarande inga rader och felet ligger kvar.
        assertEquals(
            WatchListEmptyState.LoadFailed("Kunde inte läsa in bevakningarna"),
            watchListEmptyState(visibleItemCount = 0, loadError = "Kunde inte läsa in bevakningarna")
        )
    }

    @Test
    fun `no rows without error shows no-items state`() {
        assertEquals(WatchListEmptyState.NoItems, watchListEmptyState(visibleItemCount = 0, loadError = null))
    }
}
