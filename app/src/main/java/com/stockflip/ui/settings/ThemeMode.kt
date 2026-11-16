package com.stockflip.ui.settings

/**
 * Temaval i Inställningar. [prefValue] är samma heltal som sparas under
 * `settings/night_mode` (`AppCompatDelegate.MODE_NIGHT_*`), så att valet läses likadant vid appstart.
 */
enum class ThemeMode(val prefValue: Int, val label: String) {
    System(-1, "System"),
    Light(1, "Ljust"),
    Dark(2, "Mörkt");

    /** `true`/`false` när valet är tvingat, `null` = följ systemet. */
    val forcedDark: Boolean?
        get() = when (this) {
            System -> null
            Light -> false
            Dark -> true
        }

    companion object {
        fun fromPref(value: Int): ThemeMode = entries.firstOrNull { it.prefValue == value } ?: System
    }
}
