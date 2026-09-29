package com.stockflip.ui.stockdetail

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
