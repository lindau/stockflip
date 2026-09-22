package com.stockflip

import android.content.Context
import androidx.core.content.edit

/**
 * Icke-känslig bokföring för app-uppdateringskontrollen: senaste kontrolltillfälle och
 * en ev. version användaren valt att hoppa över. Medvetet en egen, oskyddad
 * SharedPreferences-fil (som "settings" för nattläge) -- inget här är känsligt nog för
 * AppSecurityManagers AES-GCM-lagring.
 */
object AppUpdateSettings {
    private const val PREFS_NAME = "app_update"
    private const val KEY_LAST_CHECK_TS = "last_check_timestamp_millis"
    private const val KEY_SKIPPED_VERSION = "skipped_version"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLastCheckTimestampMillis(): Long = prefs.getLong(KEY_LAST_CHECK_TS, 0L)

    fun setLastCheckTimestampMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_CHECK_TS, value) }
    }

    fun getSkippedVersion(): String? = prefs.getString(KEY_SKIPPED_VERSION, null)

    fun setSkippedVersion(value: String?) {
        prefs.edit { putString(KEY_SKIPPED_VERSION, value) }
    }

    fun clearSkippedVersion() {
        prefs.edit { remove(KEY_SKIPPED_VERSION) }
    }
}
