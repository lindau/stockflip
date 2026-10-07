package com.stockflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Senaste kända kurs enligt Avanza. [epochSeconds] är tidpunkten för senaste minutstapeln. */
data class AvanzaQuote(val price: Double, val epochSeconds: Long, val previousClose: Double?)

/**
 * Reservkälla för svenska aktier när Yahoos kurs är äldre än senaste handelsdag (Yahoos realtidsfeed
 * kan fastna för enskilda bolag). Avanzas API är inofficiellt, så alla fel och timeouts ger `null`
 * och anropas bara när Yahoos kurs bevisligen är gammal.
 */
open class AvanzaQuoteService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://www.avanza.se"
) {
    private val orderBookIds = ConcurrentHashMap<String, String>()

    open suspend fun latestQuote(yahooSymbol: String): AvanzaQuote? = withContext(Dispatchers.IO) {
        try {
            val ticker = tickerOf(yahooSymbol) ?: return@withContext null
            val orderBookId = orderBookIds[yahooSymbol] ?: findOrderBookId(ticker)?.also { orderBookIds[yahooSymbol] = it }
                ?: return@withContext null
            fetchLatestBar(orderBookId)
        } catch (e: Exception) {
            null
        }
    }

    private fun findOrderBookId(ticker: String): String? {
        val body = JSONObject().apply {
            put("query", ticker)
            put("searchFilter", JSONObject().put("types", JSONArray().put("STOCK")))
            put("pagination", JSONObject().apply { put("from", 0); put("size", 10) })
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$baseUrl/_api/search/filtered-search").addHeader("User-Agent", USER_AGENT).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val hits = JSONObject(response.body?.string() ?: return null).optJSONArray("hits") ?: return null
            for (i in 0 until hits.length()) {
                val hit = hits.getJSONObject(i)
                // Titeln slutar på "(TICKER)", t.ex. "Plejd (PLEJD)" eller "Volvo B (VOLV B)". Kräv exakt ticker så att fel bolag aldrig används.
                // Dessutom svensk listning (flagCode SE), så att t.ex. EVO inte matchar den amerikanska Evotec ADR.
                val shownTicker = TICKER_IN_TITLE.find(hit.optString("title"))?.groupValues?.get(1)
                if (hit.optString("flagCode") == "SE" && shownTicker.equals(ticker, ignoreCase = true)) return hit.optString("orderBookId").takeIf { it.isNotBlank() }
            }
            return null
        }
    }

    private fun fetchLatestBar(orderBookId: String): AvanzaQuote? {
        val request = Request.Builder()
            .url("$baseUrl/_api/price-chart/stock/$orderBookId?timePeriod=today&resolution=minute")
            .addHeader("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val bars = json.optJSONArray("ohlc") ?: return null
            if (bars.length() == 0) return null
            val last = bars.getJSONObject(bars.length() - 1)
            val price = last.optDouble("close", Double.NaN)
            if (price.isNaN() || price <= 0.0) return null
            val previousClose = json.optDouble("previousClosingPrice", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
            return AvanzaQuote(price, last.optLong("timestamp") / 1000L, previousClose)
        }
    }

    internal companion object {
        private const val USER_AGENT = "StockFlip/1.0 personal-android-app"
        private val TICKER_IN_TITLE = Regex("\\(([^()]+)\\)\\s*$")

        /** `PLEJD.ST` → `PLEJD`, `VOLV-B.ST` → `VOLV B`. Bara svenska (.ST) symboler stöds. */
        fun tickerOf(yahooSymbol: String): String? =
            yahooSymbol.takeIf { it.endsWith(".ST", ignoreCase = true) }
                ?.dropLast(3)?.replace('-', ' ')?.takeIf { it.isNotBlank() }
    }
}
