package com.stockflip.viewmodel

import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.testutil.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StockSearchViewModelTest {
    private lateinit var viewModel: StockSearchViewModel
    private lateinit var repository: StockRepository
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        repository = mockk()
        viewModel = StockSearchViewModel(repository)
    }

    @Test
    fun `test search with empty query`() = runTest {
        viewModel.search("")
        advanceUntilIdle()
        assertEquals(SearchState.Success(emptyList()), viewModel.searchState.value)
        coVerify(exactly = 0) { repository.searchStocks(any<String>(), any<Boolean>()) }
    }

    @Test
    fun `test search with short query`() = runTest {
        viewModel.search("a")
        advanceUntilIdle()
        assertEquals(SearchState.Success(emptyList()), viewModel.searchState.value)
        coVerify(exactly = 0) { repository.searchStocks(any<String>(), any<Boolean>()) }
    }

    @Test
    fun `test successful search`() = runTest {
        val results = listOf(
            StockSearchResult("AAPL", "Apple Inc.", false)
        )
        coEvery { repository.searchStocks("AAPL", any()) } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(results))
        }

        viewModel.search("AAPL")
        advanceUntilIdle()
        assertEquals(SearchState.Success(results), viewModel.searchState.value)
    }

    @Test
    fun `test search error handling`() = runTest {
        val error = Exception("Network error")
        coEvery { repository.searchStocks("ERROR", any()) } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Error(error.message ?: "Unknown error", "ERROR"))
        }

        viewModel.search("ERROR")
        advanceUntilIdle()
        assertTrue(viewModel.searchState.value is SearchState.Error)
    }

    @Test
    fun `test search with empty results`() = runTest {
        coEvery { repository.searchStocks("NONEXISTENT", any()) } returns flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(emptyList()))
        }

        viewModel.search("NONEXISTENT")
        advanceUntilIdle()
        assertEquals(SearchState.Success(emptyList()), viewModel.searchState.value)
    }

    @Test
    fun `retry should re-run last failed query`() = runTest {
        val errorState = SearchState.Error("Network error", "AAPL")
        val results = listOf(StockSearchResult("AAPL", "Apple Inc.", false))

        coEvery { repository.searchStocks("AAPL", any()) } returns flow {
            emit(SearchState.Loading)
            emit(errorState)
        } andThen flow {
            emit(SearchState.Loading)
            emit(SearchState.Success(results))
        }

        viewModel.search("AAPL")
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(SearchState.Success(results), viewModel.searchState.value)
    }
} 
