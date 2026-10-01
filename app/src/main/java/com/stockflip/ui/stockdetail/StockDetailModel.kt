package com.stockflip.ui.stockdetail

import com.stockflip.isTriggeredForDisplay
import com.stockflip.ui.components.detailText
import com.stockflip.ui.watchlist.toRowModel
import com.stockflip.StockDetailData
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import com.stockflip.ui.components.formatNumber

/** En bevakningsnivå som ritas som streckad linje i grafen. */
internal data class WatchLevel(val price: Double, val label: String)

/**
 * Prisnivåer från aktiva bevakningar som går att rita som horisontell linje:
 * pris-mål, prisintervall och fall från högsta (52v/all-time-high).
 */
internal fun watchLevelsFor(alerts: List<WatchItemUiState>, data: StockDetailData): List<WatchLevel> =
    alerts.filter { it.item.isActive }.flatMap { state ->
        when (val wt = state.item.watchType) {
            is WatchType.PriceTarget -> listOf(WatchLevel(wt.targetPrice, formatNumber(wt.targetPrice)))
            is WatchType.PriceRange -> listOf(
                WatchLevel(wt.minPrice, formatNumber(wt.minPrice)),
                WatchLevel(wt.maxPrice, formatNumber(wt.maxPrice)),
            )
            is WatchType.ATHBased -> {
                val high = if (wt.reference == WatchType.HighReference.ALL_TIME_HIGH) data.allTimeHigh else data.week52High
                val level = high?.takeIf { it > 0 }?.let {
                    when (wt.dropType) {
                        WatchType.DropType.PERCENTAGE -> it * (1 - wt.dropValue / 100)
                        WatchType.DropType.ABSOLUTE -> it - wt.dropValue
                    }
                }?.takeIf { it > 0 }
                listOfNotNull(level?.let { WatchLevel(it, formatNumber(it)) })
            }
            else -> emptyList()
        }
    }.distinctBy { it.price }

/** Rad i nyckeltalstabellen. */
internal data class KeyFigure(val label: String, val value: String)

/** Nyckeltal i visningsordning; saknade värden utelämnas. */
internal fun keyFiguresFor(data: StockDetailData): List<KeyFigure> = listOfNotNull(
    data.peRatio?.takeIf { it > 0 }?.let { KeyFigure("P/E", formatNumber(it, 1)) },
    data.psRatio?.takeIf { it > 0 }?.let { KeyFigure("P/S", formatNumber(it, 1)) },
    data.dividendYield?.takeIf { it > 0 }?.let { KeyFigure("Direktavkastning", "${formatNumber(it, 1)} %") },
    data.earningsPerShare?.let { KeyFigure("Vinst per aktie", formatNumber(it)) },
    data.marketCap?.takeIf { it > 0 }?.let { KeyFigure("Börsvärde", compactMoney(it, data.currency)) },
)

internal fun compactMoney(value: Double, currency: String): String {
    val (scaled, unit) = when {
        value >= 1e12 -> value / 1e12 to "bn"
        value >= 1e9 -> value / 1e9 to "md"
        value >= 1e6 -> value / 1e6 to "mn"
        else -> value to ""
    }
    return "${formatNumber(scaled, if (unit.isEmpty()) 0 else 1)} $unit $currency".replace("  ", " ").trim()
}

/** Vad man kan göra med en bevakning direkt från raden på aktiedetaljen. */
internal enum class AlertAction(val label: String) {
    Reactivate("Återaktivera"), Resume("Aktivera"), Pause("Pausa")
}

/** Utlöst → återaktivera; pausad (ej utlöst) → aktivera; annars → pausa. */
internal fun alertActionFor(triggered: Boolean, isActive: Boolean): AlertAction = when {
    triggered -> AlertAction.Reactivate
    !isActive -> AlertAction.Resume
    else -> AlertAction.Pause
}

/** Statuspill: "Utlöst" går före "Pausad" eftersom engångsbevakningar blir inaktiva när de utlösts. */
internal fun alertStatusLabel(triggered: Boolean, isActive: Boolean): String = when {
    triggered -> "Utlöst"
    !isActive -> "Pausad"
    else -> "Väntar"
}

/** Bevakningarna på aktiedetaljen uppdelade i utlösta (nyast först) och övriga (ordningen bibehålls). */
internal data class AlertGroups(val triggered: List<com.stockflip.WatchItemUiState>, val rest: List<com.stockflip.WatchItemUiState>)

internal fun splitAlertsForDisplay(alerts: List<com.stockflip.WatchItemUiState>, triggerTimes: Map<Int, Long>): AlertGroups {
    val (triggered, rest) = alerts.partition { it.isTriggeredForDisplay() }
    return AlertGroups(triggered.sortedByDescending { triggerTimes[it.item.id] ?: 0L }, rest)
}

/** Banderoll högst upp när detaljen öppnats från en notis. [canAct] = bevakningen finns och är utlöst. */
internal data class DetailBanner(val title: String?, val message: String?, val canAct: Boolean)

internal fun detailBannerFor(title: String?, message: String?, watchTriggered: Boolean?): DetailBanner? =
    if (title.isNullOrBlank() && message.isNullOrBlank()) null
    else DetailBanner(title, message, canAct = watchTriggered == true)

private fun signedNumber(v: Double): String =
    (if (v > 0) "+" else if (v < 0) com.stockflip.ui.components.MINUS else "") + com.stockflip.ui.components.formatNumber(kotlin.math.abs(v))

/** Heroraden: "+2,95 (+1,2 %)"; bara procent eller bara belopp om det andra saknas, `null` om inget finns. */
internal fun heroChangeText(delta: Double?, percent: Double?): String? = when {
    delta == null && percent == null -> null
    delta == null -> com.stockflip.ui.components.formatSignedPercent(percent!!)
    percent == null -> signedNumber(delta)
    else -> "${signedNumber(delta)} (${com.stockflip.ui.components.formatSignedPercent(percent)})"
}

/**
 * Två rader för en bevakning på aktiedetaljen: villkoret, och (om det finns) status:
 * "Utlöst idag 09:14" för utlösta, annars aktuellt värde ("Nu −14,3 %"), eller "Pausad".
 */
internal fun alertLines(
    alert: com.stockflip.WatchItemUiState,
    triggerMillis: Long?,
    now: Long = System.currentTimeMillis(),
): Pair<String, String?> {
    val first = alert.toRowModel(triggerMillis, now).subtitle.substringBefore(" · utlöst").substringBefore(" · pausad")
    val unit = com.stockflip.ui.watchlist.currencyUnit(alert.item.ticker)
    val triggered = alert.isTriggeredForDisplay()
    val live = alert.live
    val second = when {
        triggered -> com.stockflip.ui.components.triggerWhen(triggerMillis, alert.item.lastTriggeredDate, now)?.detailText() ?: "Utlöst"
        !alert.item.isActive -> "Pausad"
        live.updateFailed -> com.stockflip.ui.components.cards.updateFailedLabel(live)
        else -> when (val wt = alert.item.watchType) {
            is com.stockflip.WatchType.PriceTarget -> live.currentPrice.takeIf { it > 0 }?.let { "Nu ${com.stockflip.ui.components.formatNumber(it)} $unit" }
            is com.stockflip.WatchType.ATHBased -> when (wt.dropType) {
                com.stockflip.WatchType.DropType.PERCENTAGE -> live.currentDropPercentage.takeIf { it > 0 }
                    ?.let { "Nu ${com.stockflip.ui.components.MINUS}${com.stockflip.ui.components.formatNumber(it, 1)} %" }
                com.stockflip.WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute.takeIf { it > 0 }
                    ?.let { "Nu ${com.stockflip.ui.components.MINUS}${com.stockflip.ui.components.formatNumber(it)} $unit" }
            }
            is com.stockflip.WatchType.KeyMetrics -> live.currentMetricValue.takeIf { it > 0 }?.let { "Nu ${com.stockflip.ui.components.formatNumber(it, 1)}" }
            is com.stockflip.WatchType.DailyMove -> live.currentDailyChangePercent?.let { "Nu ${com.stockflip.ui.components.formatSignedPercent(it)}" }
            else -> null
        }
    }
    return first to second
}
