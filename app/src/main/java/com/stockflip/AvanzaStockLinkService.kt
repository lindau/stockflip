package com.stockflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AvanzaStockLinkService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val searchUrl: String = "https://www.avanza.se/_api/search/filtered-search"
) {
    suspend fun findStockPageUrl(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("query", query)
                put("searchFilter", JSONObject().put("types", JSONArray().put("STOCK")))
                put("pagination", JSONObject().apply { put("from", 0); put("size", 1) })
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "StockFlip/1.0 personal-android-app")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val hits = json.optJSONArray("hits") ?: return@withContext null
                if (hits.length() == 0) return@withContext null
                val hit = hits.getJSONObject(0)
                val id = hit.optString("orderBookId")
                val slug = hit.optString("urlSlugName")
                if (id.isBlank() || slug.isBlank()) return@withContext null
                "https://www.avanza.se/aktier/om-aktien.html/$id/$slug"
            }
        } catch (e: Exception) {
            null
        }
    }
}
