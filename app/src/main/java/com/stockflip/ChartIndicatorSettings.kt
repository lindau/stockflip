package com.stockflip

import android.content.Context
import android.util.Log

/** Vilka indikatorer som ritas i kursgrafen. Gäller alla aktier. */
data class ChartIndicatorConfig(
    val showSma: Boolean = true,
    val showBollinger: Boolean = false,
    val showRsi: Boolean = false
)

/**
 * Sparar valet av grafindikatorer i samma "settings"-SharedPreferences som
 * [PodcastObservationSettings] och night_mode. Fel vid läsning/skrivning ger standardvärden
 * i stället för att krascha grafen.
 */
object ChartIndicatorSettings {
    private const val TAG = "ChartIndicatorSettings"
    private const val PREFS_NAME = "settings"
    private const val KEY_SHOW_SMA = "chart_show_sma"
    private const val KEY_SHOW_BOLLINGER = "chart_show_bollinger"
    private const val KEY_SHOW_RSI = "chart_show_rsi"

    fun load(context: Context): ChartIndicatorConfig {
        val defaults = ChartIndicatorConfig()
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ChartIndicatorConfig(
                showSma = prefs.getBoolean(KEY_SHOW_SMA, defaults.showSma),
                showBollinger = prefs.getBoolean(KEY_SHOW_BOLLINGER, defaults.showBollinger),
                showRsi = prefs.getBoolean(KEY_SHOW_RSI, defaults.showRsi)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Kunde inte läsa grafinställningar: ${e.message}")
            defaults
        }
    }

    fun save(context: Context, config: ChartIndicatorConfig) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOW_SMA, config.showSma)
                .putBoolean(KEY_SHOW_BOLLINGER, config.showBollinger)
                .putBoolean(KEY_SHOW_RSI, config.showRsi)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Kunde inte spara grafinställningar: ${e.message}")
        }
    }
}
