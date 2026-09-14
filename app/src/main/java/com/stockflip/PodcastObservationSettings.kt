package com.stockflip

import android.content.Context

/**
 * Runtime on/off switch for the podcast-analysis sync, independent of
 * BuildConfig.PODCAST_ANALYSIS_BASE_URL (which already keeps the feature
 * inert for every build except the developer's own -- see app/build.gradle).
 * This lets it be turned off without a rebuild, e.g. from a settings
 * screen/menu item. Stored in the same "settings" SharedPreferences file
 * StockFlipApplication already uses for night_mode.
 *
 * No UI is wired to this yet -- call setEnabled() from wherever a toggle
 * ends up living (a settings screen, an overflow menu item, ...).
 */
object PodcastObservationSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "podcast_observations_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
