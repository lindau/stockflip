package com.stockflip

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runtime on/off switch for the podcast-analysis sync, independent of
 * BuildConfig.PODCAST_ANALYSIS_BASE_URL (which already keeps the feature
 * inert for every build except the developer's own -- see app/build.gradle).
 * This lets it be turned off without a rebuild, e.g. from a settings
 * screen/menu item. Stored in the same "settings" SharedPreferences file
 * StockFlipApplication already uses for night_mode.
 *
 * Also records a small diagnostic summary of the last sync attempt
 * (PodcastObservationWorker) -- there's no server-side dashboard or adb
 * access to lean on for this personal integration, so the app itself has
 * to be able to show *why* nothing showed up (never ran yet, network
 * error, or ran fine but found no matches for any watched ticker).
 */
object PodcastObservationSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "podcast_observations_enabled"
    private const val KEY_LAST_ATTEMPT_MILLIS = "podcast_last_attempt_millis"
    private const val KEY_LAST_ERROR = "podcast_last_error"
    private const val KEY_WATCHED_COUNT = "podcast_watched_ticker_count"
    private const val KEY_MATCHED_COUNT = "podcast_matched_ticker_count"
    private const val KEY_STORED_COUNT = "podcast_stored_count"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun recordSyncResult(
        context: Context,
        watchedTickerCount: Int,
        matchedTickerCount: Int,
        storedCount: Int,
        error: String?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ATTEMPT_MILLIS, System.currentTimeMillis())
            .putString(KEY_LAST_ERROR, error)
            .putInt(KEY_WATCHED_COUNT, watchedTickerCount)
            .putInt(KEY_MATCHED_COUNT, matchedTickerCount)
            .putInt(KEY_STORED_COUNT, storedCount)
            .apply()
    }

    /** Human-readable summary of the last sync attempt, or null if it has never run. */
    fun lastSyncSummary(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAttemptMillis = prefs.getLong(KEY_LAST_ATTEMPT_MILLIS, -1L)
        if (lastAttemptMillis < 0) return null

        val time = runCatching {
            SimpleDateFormat("d MMM HH:mm", Locale("sv", "SE")).format(Date(lastAttemptMillis))
        }.getOrDefault("okänt datum")
        val error = prefs.getString(KEY_LAST_ERROR, null)
        if (error != null) {
            return "Senaste synkförsök $time misslyckades: $error"
        }
        val watched = prefs.getInt(KEY_WATCHED_COUNT, 0)
        val matched = prefs.getInt(KEY_MATCHED_COUNT, 0)
        val stored = prefs.getInt(KEY_STORED_COUNT, 0)
        return "Senaste synk $time: $matched av $watched bevakade bolag hade poddomnämnanden ($stored sparade)."
    }
}
