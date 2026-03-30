package com.stockflip

import android.content.Context
import android.content.SharedPreferences

/**
 * Spårar vilka utlösta bevakningar användaren har sett i appen.
 *
 * En bevakning anses "ny" (osedd) om [WatchItem.isTriggered] är true och dess
 * (id, lastTriggeredDate)-nyckel inte finns i den sparade mängden sedda triggers.
 *
 * Datat lagras i SharedPreferences och överlever app-omstarter.
 */
object TriggerSeenTracker {

    private const val PREFS_NAME = "trigger_seen_tracker"
    private const val KEY_SEEN_SET = "seen_trigger_keys"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        prefs.edit().putStringSet(KEY_SEEN_SET, updated).apply()
    }

    /** Markerar alla utlösta bevakningar i listan som sedda. */
    fun markAllSeen(items: List<WatchItem>) {
        val newKeys = items
            .filter { it.isTriggered && it.lastTriggeredDate != null }
            .map { seenKey(it) }
        if (newKeys.isEmpty()) return
        val updated = seenKeys().toMutableSet().also { it.addAll(newKeys) }
        prefs.edit().putStringSet(KEY_SEEN_SET, updated).apply()
    }

    private fun seenKey(item: WatchItem) = "${item.id}:${item.lastTriggeredDate}"

    private fun seenKeys(): Set<String> =
        prefs.getStringSet(KEY_SEEN_SET, emptySet()) ?: emptySet()
}
