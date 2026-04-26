package com.stockflip

import android.content.Context

/**
 * Spårar vilka utlösta bevakningar användaren har sett i appen.
 *
 * En bevakning anses "ny" (osedd) om [WatchItem.isTriggered] är true och dess
 * (id, lastTriggeredDate)-nyckel inte finns i den sparade mängden sedda triggers.
 *
 * Datat lagras krypterat i appens säkra lokala lagring och överlever app-omstarter.
 */
object TriggerSeenTracker {

    private const val KEY_SEEN_SET = "seen_trigger_keys"

    fun init(context: Context) {
        AppSecurityManager.init(context)
    }

    /** Returnerar true om bevakningen är utlöst men ännu inte sedd av användaren. */
    fun isNew(item: WatchItem): Boolean {
        if (!item.isTriggered || item.lastTriggeredDate == null) return false
        return !seenKeys().contains(seenKey(item))
    }

    /** Markerar en enstaka bevakning som sedd. */
    fun markSeen(item: WatchItem) {
        if (item.lastTriggeredDate == null) return
        val updated = seenKeys().toMutableSet().also { it.add(seenKey(item)) }
        AppSecurityManager.putStringSet(KEY_SEEN_SET, updated)
    }

    /** Markerar alla utlösta bevakningar i listan som sedda. */
    fun markAllSeen(items: List<WatchItem>) {
        val newKeys = items
            .filter { it.isTriggered && it.lastTriggeredDate != null }
            .map { seenKey(it) }
        if (newKeys.isEmpty()) return
        val updated = seenKeys().toMutableSet().also { it.addAll(newKeys) }
        AppSecurityManager.putStringSet(KEY_SEEN_SET, updated)
    }

    private fun seenKey(item: WatchItem) = "${item.id}:${item.lastTriggeredDate}"

    private fun seenKeys(): Set<String> =
        AppSecurityManager.getStringSet(KEY_SEEN_SET)
}
