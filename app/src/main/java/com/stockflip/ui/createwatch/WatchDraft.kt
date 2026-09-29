package com.stockflip.ui.createwatch

import com.stockflip.StockDetailData
import com.stockflip.WatchType
import com.stockflip.parseDecimal
import com.stockflip.ui.components.formatNumber
import kotlin.math.floor

internal enum class WatchKind(val label: String, val advanced: Boolean = false) {
    PRICE("Pris"),
    DAILY_MOVE("Dagsrörelse"),
    DRAWDOWN("Från högsta"),
    METRIC("Nyckeltal"),
    PRICE_VS_SMA("Pris mot SMA", advanced = true),
    SMA_CROSSOVER("SMA-korsning", advanced = true),
    INSIDER_BUY("Insiderköp", advanced = true),
}

/** Vad användaren fyllt i i skapa-sheeten. Fälten är råtext tills [buildWatch] tolkar dem. */
internal data class WatchDraft(
    val kind: WatchKind = WatchKind.PRICE,
    val value: String = "",
    /** Långt SMA-period vid SMA-korsning (value = kort period). */
    val value2: String = "",
    val dailyDirection: WatchType.DailyMoveDirection = WatchType.DailyMoveDirection.BOTH,
    val dropType: WatchType.DropType = WatchType.DropType.PERCENTAGE,
    val reference: WatchType.HighReference = WatchType.HighReference.FIFTY_TWO_WEEK_HIGH,
    val metric: WatchType.MetricType = WatchType.MetricType.PE_RATIO,
)

internal sealed class BuildResult {
    data class Ok(val type: WatchType, val sentence: String) : BuildResult()
    data class Invalid(val message: String) : BuildResult()
}

/** Färdiga SMA-värden (period → värde) som anroparen hämtat i förväg; behövs för att välja riktning. */
internal typealias SmaLookup = (Int) -> Double?

private fun number(v: Double) = formatNumber(v, if (v == floor(v)) 0 else 2)

private fun positive(raw: String, empty: String, invalid: String): Pair<Double?, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return null to empty
    val v = text.parseDecimal()
    return if (v != null && v.isFinite() && v > 0.0) v to null else null to invalid
}

private fun period(raw: String, empty: String): Pair<Int?, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return null to empty
    val v = text.toIntOrNull()
    return if (v != null && v in 2..500) v to null else null to "Ange ett heltal mellan 2 och 500"
}

/** SMA-perioder som [buildWatch] behöver slå upp för [draft] (för förhandshämtning). */
internal fun smaPeriodsNeeded(draft: WatchDraft): List<Int> = when (draft.kind) {
    WatchKind.PRICE_VS_SMA -> listOfNotNull(draft.value.trim().toIntOrNull())
    WatchKind.SMA_CROSSOVER -> listOfNotNull(draft.value.trim().toIntOrNull(), draft.value2.trim().toIntOrNull())
    else -> emptyList()
}

/**
 * Tolkar [draft] till en [WatchType] och en klartextmening. Riktning för pris-, nyckeltals- och
 * SMA-bevakningar härleds här (aktuellt värde ≥ mål → BELOW, annars ABOVE) — inget riktningsfält.
 */
internal fun buildWatch(draft: WatchDraft, data: StockDetailData, sma: SmaLookup = { null }): BuildResult {
    val name = data.companyName
    val unit = data.currency.takeIf { it.isNotBlank() }?.let { if (it == "SEK") "kr" else it } ?: "kr"
    fun dir(current: Double?, target: Double) =
        if (current != null && current > 0.0 && current >= target) WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
    fun verb(d: WatchType.PriceDirection) = if (d == WatchType.PriceDirection.ABOVE) "går över" else "går under"

    return when (draft.kind) {
        WatchKind.PRICE -> {
            val (target, err) = positive(draft.value, "Ange ett målpris", "Ange ett giltigt målpris")
            if (target == null) return BuildResult.Invalid(err!!)
            val d = dir(data.lastPrice, target)
            BuildResult.Ok(WatchType.PriceTarget(target, d), "Notis när $name ${verb(d)} ${formatNumber(target)} $unit.")
        }
        WatchKind.DAILY_MOVE -> {
            val (pct, err) = positive(draft.value, "Ange en procentgräns", "Ange en giltig procentgräns")
            if (pct == null) return BuildResult.Invalid(err!!)
            val text = when (draft.dailyDirection) {
                WatchType.DailyMoveDirection.UP -> "stiger mer än ${number(pct)} % på en dag"
                WatchType.DailyMoveDirection.DOWN -> "faller mer än ${number(pct)} % på en dag"
                WatchType.DailyMoveDirection.BOTH -> "rör sig mer än ${number(pct)} % på en dag"
            }
            BuildResult.Ok(WatchType.DailyMove(pct, draft.dailyDirection), "Notis när $name $text.")
        }
        WatchKind.DRAWDOWN -> {
            val (v, err) = positive(draft.value, "Ange ett värde", "Ange ett giltigt värde")
            if (v == null) return BuildResult.Invalid(err!!)
            if (draft.dropType == WatchType.DropType.PERCENTAGE && v > 100) return BuildResult.Invalid("Procent får vara högst 100")
            val ref = if (draft.reference == WatchType.HighReference.ALL_TIME_HIGH) "all-time-high" else "52-veckorshögsta"
            val amount = if (draft.dropType == WatchType.DropType.PERCENTAGE) "${number(v)} %" else "${number(v)} $unit"
            BuildResult.Ok(WatchType.ATHBased(draft.dropType, v, draft.reference), "Notis när $name har fallit $amount från $ref.")
        }
        WatchKind.METRIC -> {
            val (target, err) = positive(draft.value, "Ange ett målvärde", "Ange ett giltigt målvärde")
            if (target == null) return BuildResult.Invalid(err!!)
            val current = when (draft.metric) {
                WatchType.MetricType.PE_RATIO -> data.peRatio
                WatchType.MetricType.PS_RATIO -> data.psRatio
                WatchType.MetricType.DIVIDEND_YIELD -> data.dividendYield
                WatchType.MetricType.EARNINGS_PER_SHARE -> data.earningsPerShare
            }
            val d = dir(current, target)
            val label = when (draft.metric) {
                WatchType.MetricType.PE_RATIO -> "P/E"
                WatchType.MetricType.PS_RATIO -> "P/S"
                WatchType.MetricType.DIVIDEND_YIELD -> "direktavkastningen"
                WatchType.MetricType.EARNINGS_PER_SHARE -> "vinsten per aktie"
            }
            BuildResult.Ok(WatchType.KeyMetrics(draft.metric, target, d), "Notis när $label för $name ${verb(d)} ${number(target)}.")
        }
        WatchKind.PRICE_VS_SMA -> {
            val (p, err) = period(draft.value, "Ange antal dagar")
            if (p == null) return BuildResult.Invalid(err!!)
            val s = sma(p)
            val price = data.lastPrice
            if (s == null || s <= 0.0 || price == null) return BuildResult.Invalid("Kunde inte hämta SMA($p) just nu. Försök igen.")
            val d = if (price >= s) WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
            BuildResult.Ok(WatchType.PriceVsSma(p, d), "Notis när $name ${verb(d)} SMA $p.")
        }
        WatchKind.SMA_CROSSOVER -> {
            val (short, e1) = period(draft.value, "Ange antal dagar för kort SMA")
            if (short == null) return BuildResult.Invalid(e1!!)
            val (long, e2) = period(draft.value2, "Ange antal dagar för långt SMA")
            if (long == null) return BuildResult.Invalid(e2!!)
            if (short >= long) return BuildResult.Invalid("Långt SMA måste ha fler dagar än kort SMA")
            val a = sma(short)
            val b = sma(long)
            if (a == null || b == null) return BuildResult.Invalid("Kunde inte hämta SMA-värdena just nu. Försök igen.")
            val d = if (a >= b) WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
            BuildResult.Ok(WatchType.SmaCrossover(short, long, d), "Notis när SMA $short ${verb(d)} SMA $long.")
        }
        WatchKind.INSIDER_BUY -> BuildResult.Ok(WatchType.InsiderBuy(), "Notis när insiders köper aktier i $name.")
    }
}

/** Fyller i ett utkast från en befintlig bevakning (redigering). `null` om typen inte stöds av sheeten. */
internal fun draftFrom(type: WatchType): WatchDraft? = when (type) {
    is WatchType.PriceTarget -> WatchDraft(WatchKind.PRICE, value = number(type.targetPrice))
    is WatchType.DailyMove -> WatchDraft(WatchKind.DAILY_MOVE, value = number(type.percentThreshold), dailyDirection = type.direction)
    is WatchType.ATHBased -> WatchDraft(WatchKind.DRAWDOWN, value = number(type.dropValue), dropType = type.dropType, reference = type.reference)
    is WatchType.KeyMetrics -> WatchDraft(WatchKind.METRIC, value = number(type.targetValue), metric = type.metricType)
    is WatchType.PriceVsSma -> WatchDraft(WatchKind.PRICE_VS_SMA, value = type.period.toString())
    is WatchType.SmaCrossover -> WatchDraft(WatchKind.SMA_CROSSOVER, value = type.shortPeriod.toString(), value2 = type.longPeriod.toString())
    is WatchType.InsiderBuy -> WatchDraft(WatchKind.INSIDER_BUY)
    else -> null
}
