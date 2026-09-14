package com.stockflip

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A single podcast mention/recommendation for a ticker, imported from the
 * podcast-analysis service (see PodcastAnalysisService). Same per-ticker
 * "externally sourced structured event list" shape as InsiderTransactionEntity
 * -- source, quote, date -- just from a different source.
 */
@Entity(
    tableName = "podcast_observations",
    indices = [Index(value = ["ticker", "publishedAtMillis"])]
)
@TypeConverters(StringListConverter::class)
data class PodcastObservationEntity(
    @PrimaryKey val observationId: String,
    val ticker: String,
    val companyName: String?,
    val podcast: String,
    val episodeTitle: String?,
    val recommendation: String?,
    val stance: String?,
    val exactQuote: String?,
    val thesis: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val publishedAtMillis: Long?,
    val storedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Gson-backed List&lt;String&gt; <-> TEXT converter for Room. thesis/risks are
 * a handful of short strings per observation -- not worth a join table for.
 */
class StringListConverter {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<String>>(value, listType) }.getOrDefault(emptyList())
    }
}
