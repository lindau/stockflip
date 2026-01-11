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
        CUSTOM             // Anpassningsbar (användarens drag & drop ordning)
    }

    /**
     * Sorterar WatchItems enligt angivet läge.
     * 
     * @param items Lista av WatchItems att sortera
     * @param mode Sorteringsläge
     * @param customOrder Anpassningsbar ordning (map av ticker -> position), används endast för CUSTOM-läge
     * @return Sorterad lista
     */
    fun sortWatchItems(
        items: List<WatchItem>,
        mode: SortMode,
        customOrder: Map<String, Int> = emptyMap()
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
            SortMode.CUSTOM -> {
                // Sortera enligt customOrder, fallback till addition order om position saknas
                items.sortedWith(compareBy<WatchItem> { item ->
                    val ticker = item.ticker ?: item.ticker1 ?: ""
                    customOrder[ticker] ?: Int.MAX_VALUE
                }.thenByDescending { item -> item.id })
            }
        }
    }

    /**
     * Sorterar ticker-grupperade WatchItems.
     * 
     * @param itemsByTicker Map av ticker -> lista av WatchItems
     * @param mode Sorteringsläge
     * @param customOrder Anpassningsbar ordning
     * @return Sorterad map
     */
    fun sortTickerGroups(
        itemsByTicker: Map<String, List<WatchItem>>,
        mode: SortMode,
        customOrder: Map<String, Int> = emptyMap()
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
            SortMode.CUSTOM -> {
                itemsByTicker.toList().sortedWith(compareBy<Pair<String, List<WatchItem>>> { (ticker, _) ->
                    customOrder[ticker] ?: Int.MAX_VALUE
                }.thenByDescending { (_, items) ->
                    items.maxOfOrNull { item -> item.id } ?: 0
                })
            }
        }
        return sortedEntries.toMap()
    }
}
