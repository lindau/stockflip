package com.stockflip

import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryTriggerHistoryDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// OBS: advanceUntilIdle() används inte — följer samma försiktighet som för StockDetailViewModel.
@OptIn(ExperimentalCoroutinesApi::class)
class PairDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val pair = WatchItem(
        id = 7,
        watchType = WatchType.PricePair(priceDifference = 10.0, notifyWhenEqual = false),
        ticker1 = "INVE-B.ST",
        ticker2 = "LUND-B.ST",
        companyName1 = "Investor",
        companyName2 = "Lundbergs"
    )

    private val prices = mapOf("INVE-B.ST" to 300.0, "LUND-B.ST" to 480.0)

    private fun createViewModel(
        service: MarketDataService,
        items: List<WatchItem> = listOf(pair),
        dao: WatchItemDao = InMemoryWatchItemDao(items)
    ) = PairDetailViewModel(
        dao, service, pair.id, TriggerHistoryRepository(InMemoryTriggerHistoryDao())
    )

    @Test
    fun `load emits Success with prices and spread`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = prices))
        runCurrent()

        val state = viewModel.pairState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals(300.0, data.stockA.lastPrice!!, 0.0001)
        assertEquals(480.0, data.stockB.lastPrice!!, 0.0001)
        assertEquals(-180.0, data.spread!!, 0.0001)
    }

    @Test
    fun `first load that throws emits Error`() = runTest {
        val service = FakeMarketDataService(priceProvider = { throw IOException("offline") })
        val viewModel = createViewModel(service)
        runCurrent()

        val state = viewModel.pairState.value
        assertTrue(state is UiState.Error)
        assertEquals(PairDetailViewModel.LOAD_FAILED_MESSAGE, (state as UiState.Error).message)
    }

    @Test
    fun `first load without any price emits Error`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService())
        runCurrent()

        assertTrue(viewModel.pairState.value is UiState.Error)
    }

    @Test
    fun `one missing price still emits Success`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = mapOf("INVE-B.ST" to 300.0)))
        runCurrent()

        val state = viewModel.pairState.value
        assertTrue(state is UiState.Success)
        assertNull((state as UiState.Success).data.stockB.lastPrice)
        assertNull(state.data.spread)
    }

    @Test
    fun `retry after failed first load emits Success`() = runTest {
        var fail = true
        val service = FakeMarketDataService(priceProvider = { symbol ->
            if (fail) throw IOException("offline") else prices[symbol]
        })
        val viewModel = createViewModel(service)
        runCurrent()
        assertTrue(viewModel.pairState.value is UiState.Error)

        fail = false
        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.pairState.value is UiState.Success)
    }

    @Test
    fun `failed refresh keeps previous data and signals refreshFailed`() = runTest {
        var fail = false
        val service = FakeMarketDataService(priceProvider = { symbol ->
            if (fail) throw IOException("offline") else prices[symbol]
        })
        val viewModel = createViewModel(service)
        runCurrent()
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.refreshFailed.collect { events.add(it) }
        }

        fail = true
        viewModel.refresh()
        runCurrent()

        val state = viewModel.pairState.value
        assertTrue(state is UiState.Success)
        assertEquals(300.0, (state as UiState.Success).data.stockA.lastPrice!!, 0.0001)
        assertEquals(1, events.size)
    }

    @Test
    fun `stale slow load cannot overwrite newer successful load`() = runTest {
        var calls = 0
        val service = FakeMarketDataService(priceProvider = { symbol ->
            calls++
            // Första laddningens två prisanrop (1 och 2) är långsamma och misslyckas.
            if (calls <= 2) {
                delay(1_000L)
                throw IOException("timeout")
            }
            prices[symbol]
        })
        val viewModel = createViewModel(service) // init startar laddning 1
        runCurrent()
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.refreshFailed.collect { events.add(it) }
        }

        viewModel.refresh() // laddning 2, ska avbryta laddning 1
        advanceTimeBy(2_000L)
        runCurrent()

        val state = viewModel.pairState.value
        assertTrue(state is UiState.Success)
        assertEquals(300.0, (state as UiState.Success).data.stockA.lastPrice!!, 0.0001)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `missing pair emits Error instead of staying in Loading`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = prices), items = emptyList())
        runCurrent()

        assertTrue(viewModel.pairState.value is UiState.Error)
    }

    @Test
    fun `pair without second ticker emits Error instead of staying in Loading`() = runTest {
        val incomplete = pair.copy(ticker2 = null)
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = prices), items = listOf(incomplete))
        runCurrent()

        assertTrue(viewModel.pairState.value is UiState.Error)
    }

    @Test
    fun `deletePair returns true and removes the pair`() = runTest {
        val dao = InMemoryWatchItemDao(listOf(pair))
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = prices), dao = dao)
        runCurrent()

        assertTrue(viewModel.deletePair())
        assertNull(dao.getWatchItemById(pair.id))
    }

    @Test
    fun `deletePair returns false when the database fails`() = runTest {
        val failingDao = object : WatchItemDao by InMemoryWatchItemDao(listOf(pair)) {
            override suspend fun deleteWatchItem(item: WatchItem) {
                throw IOException("disk error")
            }
        }
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = prices), dao = failingDao)
        runCurrent()

        assertFalse(viewModel.deletePair())
    }

    @Test
    fun `deletePair returns false when pair is not loaded`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService())
        runCurrent()
        assertTrue(viewModel.pairState.value is UiState.Error)

        assertFalse(viewModel.deletePair())
    }

    @Test
    fun `reactivate returns null when pair is not loaded`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService())
        runCurrent()

        assertNull(viewModel.reactivateAndReturnResult())
    }
}
