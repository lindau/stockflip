package com.stockflip

import java.util.concurrent.ConcurrentHashMap

/**
 * Bevakningar som nollställts med "Återaktivera alla" och väntar på nästa kontroll.
 *
 * Tills bakgrundsjobbet kört igen visas de som väntande även om villkoret fortfarande är uppfyllt
 * (se `isTriggeredForDisplay`), så att det känns som en full återställning. Ligger bara i minnet:
 * efter en processomstart visas live-villkoret som vanligt.
 */
object RecheckAfterReset {
    private val ids: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun mark(items: Collection<WatchItem>) {
        ids.addAll(items.map { it.id })
    }

    /** Sant så länge bevakningen är nollställd och workern inte hunnit lösa ut den på nytt. */
    fun isAwaiting(item: WatchItem): Boolean = item.id in ids && item.isActive && !item.isTriggered

    /** Släpper bevakningar som löst ut igen, pausats eller tagits bort. */
    fun prune(items: Collection<WatchItem>) {
        if (ids.isEmpty()) return
        val awaiting = items.filter { isAwaiting(it) }.map { it.id }.toSet()
        ids.retainAll(awaiting)
    }

    internal fun clear() = ids.clear()
}
