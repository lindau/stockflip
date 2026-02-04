package com.stockflip.repository

import android.util.Log
import app.cash.turbine.test
import com.stockflip.StockSearchResult
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowLog
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StockRepositoryTest {
    private lateinit var repository: StockRepository
    private lateinit var stockSearchService: StockSearchService
    private var currentTime = 0L

    @Before
    fun setup() {
        // Set up logging
        ShadowLog.stream = System.out
        
        // Mock Android Log class
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        currentTime = 0L
        stockSearchService = mockk()
        repository = StockRepository(
            timeProvider = { currentTime },
            cacheTTL = 1000L,
            stockSearchService = stockSearchService
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `search returns cached results within TTL`() = runTest {
        val query = "volvo"
        val results = listOf(
            StockSearchResult("VOLV-B.ST", "Volvo B (Stockholmsbörsen)", true)
        )

        coEvery { stockSearchService.searchStocks(any(), any()) } returns results

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            awaitComplete()
        }

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 1) { stockSearchService.searchStocks(any(), any()) }
    }

    @Test
    fun `search returns fresh results after cache expiry`() = runTest {
        val query = "ericsson"
        val initialResults = listOf(
            StockSearchResult("ERIC-B.ST", "Ericsson B (Stockholmsbörsen)", true)
        )
        val updatedResults = listOf(
            StockSearchResult("ERIC-B.ST", "Ericsson B (Stockholmsbörsen)", true),
            StockSearchResult("ERIC-A.ST", "Ericsson A (Stockholmsbörsen)", true)
        )

        coEvery { stockSearchService.searchStocks(any(), any()) } returns initialResults

        // First search caches results
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(initialResults), awaitItem())
            awaitComplete()
        }

        // Update mock and simulate cache expiry
        coEvery { stockSearchService.searchStocks(any(), any()) } returns updatedResults
        currentTime = 2000L

        // Second search should get fresh results
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val expectedSortedResults: List<StockSearchResult> = updatedResults.sortedBy { it.symbol }
            assertEquals(SearchState.Success(expectedSortedResults), awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 2) { stockSearchService.searchStocks(any(), any()) }
    }

    @Test
    fun `search handles network error`() = runTest {
        val query = "error"
        coEvery { stockSearchService.searchStocks(any(), any()) } throws Exception("Network error")

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val error = awaitItem() as SearchState.Error
            assertEquals("Network error", error.message)
            assertEquals(query, error.lastQuery)
            awaitComplete()
        }
    }

    @Test
    fun `search handles empty results`() = runTest {
        val query = "nonexistent"
        coEvery { stockSearchService.searchStocks(any(), any()) } returns emptyList()

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `search prioritizes Swedish stocks`() = runTest {
        val query = "volvo"
        val results = listOf(
            StockSearchResult("VOW.DE", "Volkswagen AG", false),
            StockSearchResult("VOLV-B.ST", "Volvo B", true),
            StockSearchResult("VOLV-A.ST", "Volvo A", true)
        )

        coEvery { stockSearchService.searchStocks(any(), any()) } returns results

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val success = awaitItem() as SearchState.Success
            assertTrue(success.results[0].isSwedish)
            assertTrue(success.results[1].isSwedish)
            assertTrue(!success.results[2].isSwedish)
            awaitComplete()
        }
    }

    @Test
    fun `partial search returns relevant results`() = runTest {
        val query = "vol"
        val results = listOf(
            StockSearchResult("VOLV-B.ST", "Volvo B", true),
            StockSearchResult("VOW.DE", "Volkswagen AG", false)
        )

        coEvery { stockSearchService.searchStocks(any(), any()) } returns results

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val success = awaitItem() as SearchState.Success
            assertTrue(success.results.isNotEmpty())
            assertTrue(success.results.any { it.symbol.contains("VOLV") })
            awaitComplete()
        }
    }

    @Test
    fun `test search with empty query`() = runTest {
        repository.searchStocks("").test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 0) { stockSearchService.searchStocks(any(), any()) }
    }

    @Test
    fun `test search with short query`() = runTest {
        repository.searchStocks("a").test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 0) { stockSearchService.searchStocks(any(), any()) }
    }

    @Test
    fun `test successful search`() = runTest {
        val results = listOf(
            StockSearchResult("AAPL", "Apple Inc.", false)
        )
        coEvery { stockSearchService.searchStocks(any(), any()) } returns results

        repository.searchStocks("AAPL").test {
            assertEquals(SearchState.Loading, awaitItem())
            val success = awaitItem() as SearchState.Success
            assertEquals(results, success.results)
            awaitComplete()
        }
    }

    @Test
    fun `test search error handling`() = runTest {
        coEvery { stockSearchService.searchStocks(any(), any()) } throws Exception("Network error")

        repository.searchStocks("ERROR").test {
            assertEquals(SearchState.Loading, awaitItem())
            assertTrue(awaitItem() is SearchState.Error)
            awaitComplete()
        }
    }
} 
