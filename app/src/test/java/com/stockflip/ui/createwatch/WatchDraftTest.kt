package com.stockflip.ui.createwatch

import com.stockflip.StockDetailData
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDraftTest {
    private val data = StockDetailData(
        symbol = "VOLV-B.ST", companyName = "Volvo B", lastPrice = 250.0, previousClose = 249.0,
        week52High = 300.0, week52Low = 200.0, dailyChangePercent = 0.4, drawdownPercent = null,
        peRatio = 10.0,
    )

    private fun ok(d: WatchDraft, sma: SmaLookup = { null }) = buildWatch(d, data, sma) as BuildResult.Ok

    @Test fun priceBelowCurrentInfersBelowAndSentence() {
        val r = ok(WatchDraft(WatchKind.PRICE, value = "245,00"))
        assertEquals(WatchType.PriceTarget(245.0, WatchType.PriceDirection.BELOW), r.type)
        assertEquals("Notis när Volvo B går under 245,00 kr.", r.sentence)
    }

    @Test fun priceAboveCurrentInfersAbove() {
        val r = ok(WatchDraft(WatchKind.PRICE, value = "300"))
        assertEquals(WatchType.PriceDirection.ABOVE, (r.type as WatchType.PriceTarget).direction)
    }

    @Test fun emptyAndInvalidGiveMessages() {
        assertEquals(BuildResult.Invalid("Ange ett målpris"), buildWatch(WatchDraft(value = ""), data))
        assertEquals(BuildResult.Invalid("Ange ett giltigt målpris"), buildWatch(WatchDraft(value = "-3"), data))
    }

    @Test fun drawdownPercentOver100Rejected() {
        assertTrue(buildWatch(WatchDraft(WatchKind.DRAWDOWN, value = "120"), data) is BuildResult.Invalid)
        val r = ok(WatchDraft(WatchKind.DRAWDOWN, value = "15"))
        assertEquals(WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 15.0), r.type)
    }

    @Test fun metricDirectionInferredFromCurrent() {
        val r = ok(WatchDraft(WatchKind.METRIC, value = "8"))
        assertEquals(WatchType.PriceDirection.BELOW, (r.type as WatchType.KeyMetrics).direction)
    }

    @Test fun smaNeedsLookupAndInfersDirection() {
        val d = WatchDraft(WatchKind.PRICE_VS_SMA, value = "200")
        assertTrue(buildWatch(d, data) is BuildResult.Invalid)
        assertEquals(WatchType.PriceDirection.ABOVE, (ok(d) { 260.0 }.type as WatchType.PriceVsSma).direction)
        assertEquals(listOf(200), smaPeriodsNeeded(d))
    }

    @Test fun crossoverRequiresShortBelowLong() {
        val bad = WatchDraft(WatchKind.SMA_CROSSOVER, value = "200", value2 = "50")
        assertEquals(BuildResult.Invalid("Långt SMA måste ha fler dagar än kort SMA"), buildWatch(bad, data) { 1.0 })
    }
}
