package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastAnalysisServiceTest {

    @Test
    fun `toEntity maps a mention DTO to an entity keyed by the given ticker`() {
        val dto = PodcastMentionDto(
            observation_id = "obs-1",
            podcast = "10x-podden",
            title = "Avsnitt 42",
            published = "Thu, 18 Jun 2026 22:01:00 GMT",
            recommendation = "Köp",
            stance = "Positiv",
            stance_raw = "positive",
            thesis = listOf("Stark tillväxt"),
            risks = listOf("Värdering")
        )

        val entity = dto.toEntity(ticker = "VOLV-B.ST", companyName = "Volvo")

        assertEquals("obs-1", entity?.observationId)
        assertEquals("VOLV-B.ST", entity?.ticker)
        assertEquals("Volvo", entity?.companyName)
        assertEquals("10x-podden", entity?.podcast)
        assertEquals("Avsnitt 42", entity?.episodeTitle)
        assertEquals("Köp", entity?.recommendation)
        assertEquals(listOf("Stark tillväxt"), entity?.thesis)
        assertEquals(listOf("Värdering"), entity?.risks)
        assertTrue((entity?.publishedAtMillis ?: 0L) > 0L)
    }

    @Test
    fun `toEntity returns null when the mention has no observation id`() {
        val dto = PodcastMentionDto(observation_id = null, podcast = "10x-podden")

        assertNull(dto.toEntity(ticker = "VOLV-B.ST", companyName = null))
    }

    @Test
    fun `parsePublishedAtMillis parses the RFC-1123-ish published date`() {
        val millis = parsePublishedAtMillis("Thu, 18 Jun 2026 22:01:00 GMT")

        assertTrue((millis ?: 0L) > 0L)
    }

    @Test
    fun `parsePublishedAtMillis returns null for blank or unparseable input`() {
        assertNull(parsePublishedAtMillis(null))
        assertNull(parsePublishedAtMillis(""))
        assertNull(parsePublishedAtMillis("not a date"))
    }

    @Test
    fun `getObservationsByTicker returns empty map when no base URL is configured`() = kotlinx.coroutines.runBlocking {
        val service = PodcastAnalysisService(baseUrl = "")

        assertEquals(false, service.isConfigured)
        assertEquals(emptyMap<String, List<PodcastMentionDto>>(), service.getObservationsByTicker())
    }
}
