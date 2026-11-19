package com.stockflip

import com.google.gson.Gson
import com.stockflip.ui.components.MentionMarker
import com.stockflip.ui.components.SignalTone
import com.stockflip.ui.components.chartIndexFor
import com.stockflip.ui.components.mentionMarkersFrom
import com.stockflip.ui.components.podcastFlagLabel
import com.stockflip.ui.components.summaryText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastSignalsTest {

    private val json = """
        {"companies":[{"ticker":"GOOG","name":"Alphabet Inc.","market":"us","pods":8,"episodes":21,"net":0.253,
          "pos_pods":6,"neg_pods":1,"flags":["konsensus_pos","ökar","okänd_flagga"],"score":46.3,
          "price":{"pct":-11.8,"first_date":"2026-01-05","currency":""},"extra_field":1}]}
    """.trimIndent()

    @Test
    fun `signals response parses and ignores unknown fields`() {
        val dto = Gson().fromJson(json, PodcastSignalsResponse::class.java).companies.single()
        assertEquals("GOOG", dto.ticker)
        assertEquals(8, dto.pods)
        assertEquals(-11.8, dto.price?.pct!!, 0.001)
    }

    @Test
    fun `flag labels are Swedish and unknown flags are ignored`() {
        assertEquals("Konsensus positiv", podcastFlagLabel("konsensus_pos")?.text)
        assertEquals(SignalTone.Negative, podcastFlagLabel("vänt_neg")?.tone)
        assertNull(podcastFlagLabel("okänd_flagga"))
    }

    @Test
    fun `summary text pluralises and names sentiment`() {
        val s = PodcastSignalDto(pods = 1, episodes = 3, net = -0.9)
        assertEquals("1 podd · 3 avsnitt · negativt", s.summaryText())
    }

    @Test
    fun `repository caches within ttl and returns stale data on failure`() = runBlocking {
        var calls = 0
        var fail = false
        var time = 0L
        val signal = PodcastSignalDto(ticker = "GOOG", score = 1.0, market = "us")
        val repo = PodcastSignalsRepository(
            fetch = { calls++; if (fail) throw RuntimeException("nät") else listOf(signal) },
            now = { time },
            ttlMillis = 1000,
        )
        assertNotNull(repo.forTicker("goog"))
        assertNotNull(repo.forTicker("GOOG"))
        assertEquals(1, calls)
        time = 2000; fail = true
        assertNotNull(repo.forTicker("GOOG"))
        assertEquals(2, calls)
        assertEquals(emptyList<PodcastSignalDto>(), repo.top("se"))
    }

    @Test
    fun `repository returns empty when first fetch fails`() = runBlocking {
        val repo = PodcastSignalsRepository(fetch = { throw RuntimeException("nät") })
        assertNull(repo.forTicker("GOOG"))
    }

    @Test
    fun `markers merge per day and mixed tone becomes neutral`() {
        fun obs(id: String, millis: Long, stance: String) = PodcastObservationEntity(
            observationId = id, ticker = "X", companyName = null, podcast = "p", episodeTitle = null,
            recommendation = null, stance = stance, exactQuote = null, publishedAtMillis = millis,
        )
        val day = 1_700_000_000_000L
        val markers = mentionMarkersFrom(
            listOf(obs("a", day, "Positiv"), obs("b", day + 1000, "Negativ"), obs("c", day + 86_400_000L * 2, "Starkt positiv"))
        )
        assertEquals(2, markers.size)
        assertEquals(0, markers[0].tone)
        assertEquals(listOf("p · Positiv", "p · Negativ"), markers[0].notes)
        assertEquals(1, markers[1].tone)
    }

    @Test
    fun `chart index is null outside the chart range and nearest inside`() {
        val ts = listOf(0L, 100_000L, 200_000L)
        assertNull(chartIndexFor(ts, 1_000_000L))
        assertEquals(1, chartIndexFor(ts, 90_000L))
        assertEquals(MentionMarker(1L, 1), MentionMarker(1L, 1))
    }
}
