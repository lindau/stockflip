package com.stockflip.viewmodel

import app.cash.turbine.test
import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import io.mockk.coEvery
import io.mockk.mockk
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
        coEvery { repository.searchStocks("") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(emptyList()))
        }

        viewModel.search("")
        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test search with short query`() = runTest {
        coEvery { repository.searchStocks("a") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(emptyList()))
        }

        viewModel.search("a")
        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
            assertEquals(SearchState.Loading, awaitItem())
            assertTrue(awaitItem() is SearchState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test search debounce`() = runTest {
        val results = listOf(
            StockSearchResult("AAPL", "Apple Inc.", false),
            StockSearchResult("AAPL.ST", "Apple Inc. (Stockholm)", false)
        )
        coEvery { repository.searchStocks("AAPL") } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(results))
        }

        viewModel.search("A")
        viewModel.search("AA")
        viewModel.search("AAP")
        viewModel.search("AAPL")

        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
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
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
} 
