package com.stockflip.repository

import app.cash.turbine.test
import com.stockflip.StockSearchResult
import com.stockflip.YahooFinanceService
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StockRepositoryTest {
    private lateinit var repository: StockRepository

    @Before
    fun setup() {
        repository = StockRepository()
        mockkObject(YahooFinanceService)
    }

    @Test
    fun `search returns cached results within TTL`() = runTest {
        val query = "volvo"
        val results = listOf(
            StockSearchResult("VOLV-B.ST", "Volvo B (Stockholmsbörsen)", true)
        )

        coEvery { YahooFinanceService.searchStocks(query) } returns results

        // First search
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            awaitComplete()
        }

        // Second search should use cache
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            awaitComplete()
        }
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

        coEvery { YahooFinanceService.searchStocks(query) } returns initialResults

        // First search
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(initialResults), awaitItem())
            awaitComplete()
        }

        // Update mock and simulate cache expiry
        coEvery { YahooFinanceService.searchStocks(query) } returns updatedResults
        repository.cleanExpiredCache()

        // Second search should get fresh results
        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(updatedResults), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `search handles network error`() = runTest {
        val query = "error"
        coEvery { YahooFinanceService.searchStocks(query) } throws Exception("Network error")

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val error = awaitItem() as SearchState.Error
            assertTrue(error.message.contains("Network error"))
            awaitComplete()
        }
    }

    @Test
    fun `search handles empty results`() = runTest {
        val query = "nonexistent"
        coEvery { YahooFinanceService.searchStocks(query) } returns emptyList()

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

        coEvery { YahooFinanceService.searchStocks(query) } returns results

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

        coEvery { YahooFinanceService.searchStocks(query) } returns results

        repository.searchStocks(query).test {
            assertEquals(SearchState.Loading, awaitItem())
            val success = awaitItem() as SearchState.Success
            assertTrue(success.results.isNotEmpty())
            assertTrue(success.results.any { it.symbol.contains("VOLV") })
            awaitComplete()
        }
    }
} 