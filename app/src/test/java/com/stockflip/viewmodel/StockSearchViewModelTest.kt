package com.stockflip.viewmodel

import app.cash.turbine.test
import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import androidx.lifecycle.ViewModel

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StockSearchViewModelTest {
    private lateinit var viewModel: StockSearchViewModel
    private lateinit var repository: StockRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = TestStockSearchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty query emits no results`() = runTest {
        val query = ""
        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Success(emptyList())
        )

        viewModel.search(query)

        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `single character query emits no results`() = runTest {
        val query = "a"
        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Success(emptyList())
        )

        viewModel.search(query)

        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid query returns results`() = runTest {
        val query = "volvo"
        val results = listOf(
            StockSearchResult("VOLV-B.ST", "Volvo B (Stockholmsbörsen)", true)
        )

        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Success(results)
        )

        viewModel.search(query)

        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error in search shows error state`() = runTest {
        val query = "error"
        val errorMessage = "Network error"

        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Error(errorMessage)
        )

        viewModel.search(query)

        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            val error = awaitItem() as SearchState.Error
            assertTrue(error.message.contains(errorMessage))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry mechanism works on error`() = runTest {
        val query = "retry"
        val results = listOf(
            StockSearchResult("TEST.ST", "Test Stock", true)
        )

        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Error("First attempt failed")
        ) andThen flowOf(
            SearchState.Loading,
            SearchState.Success(results)
        )

        viewModel.search(query)
        viewModel.retry()

        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertTrue(awaitItem() is SearchState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debounce prevents rapid searches`() = runTest {
        val results1 = listOf(StockSearchResult("TEST1.ST", "Test Stock 1", true))
        val results2 = listOf(StockSearchResult("TEST2.ST", "Test Stock 2", true))

        coEvery { repository.searchStocks("test1") } returns flowOf(
            SearchState.Loading,
            SearchState.Success(results1)
        )
        coEvery { repository.searchStocks("test2") } returns flowOf(
            SearchState.Loading,
            SearchState.Success(results2)
        )

        viewModel.search("test1")
        viewModel.search("test2")

        viewModel.searchState.test {
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Success(results2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cleanup cancels ongoing jobs`() = runTest {
        val query = "test"
        val results = listOf(StockSearchResult("TEST.ST", "Test Stock", true))

        coEvery { repository.searchStocks(query) } returns flowOf(
            SearchState.Loading,
            SearchState.Success(results)
        )

        viewModel.search(query)
        (viewModel as TestStockSearchViewModel).cleanup()

        viewModel.searchState.test {
            assertEquals(SearchState.Success(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class TestStockSearchViewModel(repository: StockRepository) : StockSearchViewModel(repository) {
        fun cleanup() {
            onCleared()
        }
    }
} 
