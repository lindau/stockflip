package com.stockflip

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object NotificationNavigationSecurity {
    private const val TOKENS_KEY = "notification_navigation_tokens"
    private const val TOKEN_TTL_MS = 15 * 60 * 1000L

    fun issueToken(): String {
        val now = System.currentTimeMillis()
        val entries = loadEntries()
            .filter { it.expiresAt > now }
            .toMutableList()
        val token = UUID.randomUUID().toString()
        entries.add(TokenEntry(token = token, expiresAt = now + TOKEN_TTL_MS))
        saveEntries(entries)
        return token
    }

    fun consumeToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false

        val now = System.currentTimeMillis()
        var valid = false
        val remaining = loadEntries()
            .filter { entry ->
                when {
                    entry.expiresAt <= now -> false
                    entry.token == token -> {
                        valid = true
                        false
                    }
                    else -> true
                }
            }

        saveEntries(remaining)
        return valid
    }

    private fun loadEntries(): List<TokenEntry> {
        val raw = AppSecurityManager.getString(TOKENS_KEY) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                add(
                    TokenEntry(
                        token = json.getString("token"),
                        expiresAt = json.getLong("expiresAt")
                    )
                )
            }
        }
    }

    private fun saveEntries(entries: List<TokenEntry>) {
        if (entries.isEmpty()) {
            AppSecurityManager.putString(TOKENS_KEY, null)
            return
        }

        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("token", entry.token)
                    put("expiresAt", entry.expiresAt)
                }
            )
        }
        AppSecurityManager.putString(TOKENS_KEY, array.toString())
    }

    private data class TokenEntry(
        val token: String,
        val expiresAt: Long
    )
}
