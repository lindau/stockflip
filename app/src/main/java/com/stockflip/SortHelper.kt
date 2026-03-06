package com.stockflip

/**
 * Helper för att sortera WatchItems.
 */
object SortHelper {
    /**
     * Sorteringsläge för aktier.
     */
    enum class SortMode {
        ALPHABETICAL,      // Bokstavsordning (A-Z)
        ADDITION_ORDER,    // Tilläggsordning (senaste först, baserat på id)
    }

    /**
     * Sorterar WatchItems enligt angivet läge.
     *
     * @param items Lista av WatchItems att sortera
     * @param mode Sorteringsläge
     * @return Sorterad lista
     */
    fun sortWatchItems(
        items: List<WatchItem>,
        mode: SortMode
    ): List<WatchItem> {
        return when (mode) {
            SortMode.ALPHABETICAL -> {
                items.sortedWith(compareBy { item ->
                    val name = item.companyName ?: item.ticker ?: item.ticker1 ?: ""
                    name.lowercase()
                })
            }
            SortMode.ADDITION_ORDER -> {
                // Använd id som proxy för tilläggsordning (högre id = senare tillagd)
                items.sortedByDescending { it.id }
            }
        }
    }

    /**
     * Sorterar ticker-grupperade WatchItems.
     *
     * @param itemsByTicker Map av ticker -> lista av WatchItems
     * @param mode Sorteringsläge
     * @return Sorterad map
     */
    fun sortTickerGroups(
        itemsByTicker: Map<String, List<WatchItem>>,
        mode: SortMode
    ): Map<String, List<WatchItem>> {
        val sortedEntries = when (mode) {
            SortMode.ALPHABETICAL -> {
                itemsByTicker.toList().sortedBy { (ticker, _) ->
                    ticker.lowercase()
                }
            }
            SortMode.ADDITION_ORDER -> {
                // Sortera efter högsta id i gruppen (senaste tillagda)
                itemsByTicker.toList().sortedByDescending { (_, items) ->
                    items.maxOfOrNull { it.id } ?: 0
                }
            }
        }
        return sortedEntries.toMap()
    }
}
