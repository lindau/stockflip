package com.stockflip.viewmodel

import app.cash.turbine.test
import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StockSearchViewModelTest {
    private lateinit var viewModel: StockSearchViewModel
    private lateinit var repository: StockRepository

    @Before
    fun setup() {
        repository = mockk()
        viewModel = StockSearchViewModel(repository)
    }

    @Test
    fun `test search with empty query`() = runTest {
        viewModel.search("")
        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchStocks(any()) }
    }

    @Test
    fun `test search with short query`() = runTest {
        viewModel.search("a")
        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchStocks(any()) }
    }

    @Test
    fun `test successful search`() = runTest {
        val results = listOf(
            StockSearchResult("AAPL", "Apple Inc.", false)
        )
        coEvery { repository.searchStocks("AAPL") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(results))
        }

        viewModel.search("AAPL")
        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test search error handling`() = runTest {
        val error = Exception("Network error")
        coEvery { repository.searchStocks("ERROR") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Error(error.message ?: "Unknown error", "ERROR"))
        }

        viewModel.search("ERROR")
        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            assertEquals(SearchState.Loading, awaitItem())
            assertTrue(awaitItem() is SearchState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test search with empty results`() = runTest {
        coEvery { repository.searchStocks("NONEXISTENT") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(emptyList()))
        }

        viewModel.search("NONEXISTENT")
        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry should re-run last failed query`() = runTest {
        val errorState = SearchState.Error("Network error", "AAPL")
        val results = listOf(StockSearchResult("AAPL", "Apple Inc.", false))

        coEvery { repository.searchStocks("AAPL") } returns flow {
            emit(SearchState.Loading)
            emit(errorState)
        } andThen flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(results))
        }

        viewModel.search("AAPL")
        viewModel.retry()

        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(errorState, awaitItem())
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
} 
