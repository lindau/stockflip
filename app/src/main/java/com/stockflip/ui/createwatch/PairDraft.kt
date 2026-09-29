package com.stockflip.ui.createwatch

import com.stockflip.StockSearchResult
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.formatNumber
import com.stockflip.ui.dialogs.parsePairSpread

internal sealed class PairResult {
    data class Ok(val item: WatchItem, val sentence: String) : PairResult()
    data class Invalid(val message: String) : PairResult()
}

/**
 * Bygger en parbevakning. Tom prisskillnad = 0 (bevaka när kurserna möts). [existing] behåller id och
 * spamskyddsfält vid redigering.
 */
internal fun buildPair(
    a: StockSearchResult?,
    b: StockSearchResult?,
    spreadRaw: String,
    notifyWhenEqual: Boolean,
    existing: WatchItem? = null,
): PairResult {
    if (a == null) return PairResult.Invalid("Välj första aktien")
    if (b == null) return PairResult.Invalid("Välj andra aktien")
    if (a.symbol == b.symbol) return PairResult.Invalid("Välj två olika aktier")
    val spread = parsePairSpread(spreadRaw) ?: return PairResult.Invalid("Ange en giltig prisskillnad")
    val type = WatchType.PricePair(spread, notifyWhenEqual)
    val item = (existing ?: WatchItem(watchType = type)).copy(
        watchType = type,
        ticker1 = a.symbol, ticker2 = b.symbol,
        companyName1 = a.name, companyName2 = b.name,
    )
    val sentence = if (spread == 0.0) "Notis när ${a.name} och ${b.name} har samma kurs."
    else "Notis när skillnaden mellan ${a.name} och ${b.name} når ${formatNumber(spread)}."
    return PairResult.Ok(item, sentence)
}
