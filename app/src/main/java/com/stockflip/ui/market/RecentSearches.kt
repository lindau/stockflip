package com.stockflip.ui.market

import android.content.Context
import com.stockflip.StockSearchResult

/** Senast öppnade sökträffar, nyast först. Lagras som `symbol<TAB>namn` per rad i SharedPreferences. */
internal object RecentSearches {
    const val MAX = 10
    private const val PREFS = "market"
    private const val KEY = "recent"

    /** Lägger [result] först, tar bort dubbletter (på symbol) och kapar listan till [MAX]. */
    fun add(current: List<StockSearchResult>, result: StockSearchResult): List<StockSearchResult> =
        (listOf(result) + current.filterNot { it.symbol == result.symbol }).take(MAX)

    fun encode(items: List<StockSearchResult>): String =
        items.joinToString("\n") { "${clean(it.symbol)}\t${clean(it.name)}" }

    fun decode(raw: String?): List<StockSearchResult> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val symbol = line.substringBefore('\t').trim()
            val name = line.substringAfter('\t', "").trim()
            if (symbol.isEmpty()) null else StockSearchResult(
                symbol = symbol,
                name = name.ifEmpty { symbol },
                isSwedish = symbol.endsWith(".ST"),
                isCrypto = StockSearchResult.isCryptoSymbol(symbol),
                isIndex = StockSearchResult.isIndexSymbol(symbol),
            )
        }.take(MAX).toList()

    fun load(context: Context): List<StockSearchResult> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, items: List<StockSearchResult>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(items)).apply()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
