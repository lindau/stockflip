package com.stockflip.repository

import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastMentionRepositoryTest {
    private lateinit var api: PodcastMentionApi
    private var currentTime = 0L

    private fun repository(api: PodcastMentionApi?) = PodcastMentionRepository(
        timeProvider = { currentTime },
        cacheTTL = 1000L,
        api = api,
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        currentTime = 0L
        api = mockk()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `returns normalized tickers from api`() = runTest {
        coEvery { api.getMentionedTickers() } returns MentionedTickersResponse(listOf("VOLV-B.ST", "acme"))

        val result = repository(api).getMentionedTickers()

        assertEquals(setOf("VOLVB", "ACME"), result)
    }

    @Test
    fun `returns cached results within TTL without calling api again`() = runTest {
        coEvery { api.getMentionedTickers() } returns MentionedTickersResponse(listOf("ACME"))
        val repo = repository(api)

        repo.getMentionedTickers()
        currentTime = 500L
        val result = repo.getMentionedTickers()

        assertEquals(setOf("ACME"), result)
        io.mockk.coVerify(exactly = 1) { api.getMentionedTickers() }
    }

    @Test
    fun `refetches after TTL expires`() = runTest {
        coEvery { api.getMentionedTickers() } returns MentionedTickersResponse(listOf("ACME"))
        val repo = repository(api)

        repo.getMentionedTickers()
        currentTime = 1500L
        repo.getMentionedTickers()

        io.mockk.coVerify(exactly = 2) { api.getMentionedTickers() }
    }

    @Test
    fun `returns empty set when api is null (feature disabled)`() = runTest {
        val result = repository(null).getMentionedTickers()

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `falls back to last cached value when api call fails`() = runTest {
        coEvery { api.getMentionedTickers() } returns MentionedTickersResponse(listOf("ACME"))
        val repo = repository(api)
        repo.getMentionedTickers()

        currentTime = 1500L
        coEvery { api.getMentionedTickers() } throws java.io.IOException("unreachable")
        val result = repo.getMentionedTickers()

        assertEquals(setOf("ACME"), result)
    }

    @Test
    fun `normalizeTicker strips exchange suffix, dashes and spaces`() {
        assertEquals("VOLVB", PodcastMentionRepository.normalizeTicker("VOLV-B.ST"))
        assertEquals("VOLVB", PodcastMentionRepository.normalizeTicker("volv b"))
        assertEquals("ACME", PodcastMentionRepository.normalizeTicker("acme"))
    }
}
