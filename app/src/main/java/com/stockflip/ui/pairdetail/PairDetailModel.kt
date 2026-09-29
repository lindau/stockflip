package com.stockflip.ui.pairdetail

import com.stockflip.CurrencyHelper
import com.stockflip.PairDetailData
import com.stockflip.WatchType
import com.stockflip.hasPendingNextTradingDayGuard
import com.stockflip.pairSpreadDirectionLabel
import kotlin.math.abs

internal enum class PairStatus(val label: String) {
    Triggered("Utlöst"), NextTradingDay("Nästa handelsdag"), Active("Aktiv"), Inactive("Pausad")
}

/** Färdiga värden för pardetaljen; ren mappning från [PairDetailData] (testbar utan Compose). */
internal data class PairDetailModel(
    val title: String,
    val status: PairStatus,
    val spread: String,
    val spreadLabel: String,
    val trigger: String,
    val notifyWhenEqual: Boolean,
    val remaining: String,
)

internal fun PairDetailData.toModel(): PairDetailModel? {
    val pair = watchItem.watchType as? WatchType.PricePair ?: return null
    val current = spread?.let { abs(it) }
    val target = pair.priceDifference.takeIf { it > 0.0 }
    val waiting = watchItem.hasPendingNextTradingDayGuard()
    val triggered = !waiting && (
        watchItem.isTriggered ||
            (current != null && target != null && current >= target) ||
            (pair.notifyWhenEqual && current != null && current < 0.01)
        )
    val status = when {
        watchItem.isTriggered -> PairStatus.Triggered
        waiting -> PairStatus.NextTradingDay
        triggered -> PairStatus.Triggered
        watchItem.isActive -> PairStatus.Active
        else -> PairStatus.Inactive
    }
    return PairDetailModel(
        title = "${stockA.companyName ?: stockA.symbol} / ${stockB.companyName ?: stockB.symbol}",
        status = status,
        spread = current?.let { CurrencyHelper.formatDecimal(it) } ?: "–",
        spreadLabel = pairSpreadDirectionLabel(stockA.lastPrice, stockB.lastPrice, stockA.symbol, stockB.symbol),
        trigger = target?.let { "≥ ${CurrencyHelper.formatDecimal(it)}" } ?: "När kurserna möts",
        notifyWhenEqual = pair.notifyWhenEqual,
        remaining = if (current == null || target == null) "–"
        else CurrencyHelper.formatDecimal((target - current).coerceAtLeast(0.0)),
    )
}
