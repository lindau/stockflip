package com.stockflip

import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryMetricHistoryDao
import com.stockflip.testutil.InMemoryStockNoteDao
import com.stockflip.testutil.InMemoryTriggerHistoryDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadStockData emits Success with expected fields`() = runTest {
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(emptyList())
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf("VOLV-B.ST" to 300.0),
            previousCloseBySymbol = mapOf("VOLV-B.ST" to 295.0),
            currencyBySymbol = mapOf("VOLV-B.ST" to "SEK"),
            exchangeBySymbol = mapOf("VOLV-B.ST" to "STO"),
            companyNameBySymbol = mapOf("VOLV-B.ST" to "Volvo B")
        )

        val viewModel = StockDetailViewModel(watchItemDao, marketDataService, "VOLV-B.ST",
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao()))
        viewModel.loadStockData()
        advanceUntilIdle()
        val state: UiState<StockDetailData> = viewModel.stockDataState.value
        val success: UiState.Success<StockDetailData> = state as UiState.Success<StockDetailData>
        val data: StockDetailData = success.data
        assertEquals("VOLV-B.ST", data.symbol)
        assertEquals("Volvo B", data.companyName)
        assertEquals(300.0, data.lastPrice!!, 0.0001)
        assertEquals(295.0, data.previousClose!!, 0.0001)
        assertEquals("SEK", data.currency)
        assertEquals("STO", data.exchange)
        val expectedChangePercent: Double = ((300.0 - 295.0) / 295.0) * 100
        assertEquals(expectedChangePercent, data.dailyChangePercent!!, 0.0001)
    }

    @Test
    fun `loadStockData sets dailyChangePercent null when snapshot has no previousClose`() = runTest {
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(emptyList())
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf("DELIA.OL" to 413.0),
            previousCloseBySymbol = emptyMap(),
            currencyBySymbol = mapOf("DELIA.OL" to "NOK"),
            exchangeBySymbol = mapOf("DELIA.OL" to "OSE"),
            companyNameBySymbol = mapOf("DELIA.OL" to "DELLIA GROUP")
        )
        val viewModel = StockDetailViewModel(watchItemDao, marketDataService, "DELIA.OL",
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao()))
        viewModel.loadStockData()
        advanceUntilIdle()
        val state: UiState<StockDetailData> = viewModel.stockDataState.value
        val success: UiState.Success<StockDetailData> = state as UiState.Success<StockDetailData>
        val data: StockDetailData = success.data
        assertEquals(413.0, data.lastPrice!!, 0.0001)
        assertEquals(null, data.previousClose)
        assertEquals(null, data.dailyChangePercent)
    }

    @Test
    fun `alerts are sorted by ascending price target on stock detail page`() = runTest {
        val symbol = "VOLV-B.ST"
        val watchItems = listOf(
            WatchItem(id = 4, watchType = WatchType.PriceTarget(4.0, WatchType.PriceDirection.ABOVE), ticker = symbol),
            WatchItem(id = 3, watchType = WatchType.PriceTarget(3.0, WatchType.PriceDirection.ABOVE), ticker = symbol),
            WatchItem(id = 2, watchType = WatchType.PriceTarget(2.0, WatchType.PriceDirection.BELOW), ticker = symbol),
            WatchItem(id = 5, watchType = WatchType.PriceTarget(5.0, WatchType.PriceDirection.ABOVE), ticker = symbol),
            WatchItem(id = 1, watchType = WatchType.PriceTarget(1.0, WatchType.PriceDirection.BELOW), ticker = symbol)
        )
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(watchItems)
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(symbol to 3.0),
            previousCloseBySymbol = mapOf(symbol to 3.0),
            chartDataByPeriod = mapOf(ChartPeriod.DAY to IntradayChartData(emptyList(), emptyList(), null))
        )

        val viewModel = StockDetailViewModel(
            watchItemDao,
            marketDataService,
            symbol,
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()),
            InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
        )
        advanceUntilIdle()

        val state = viewModel.alertsState.value as UiState.Success<List<WatchItemUiState>>
        val priceTargets = state.data.map { (it.item.watchType as WatchType.PriceTarget).targetPrice }
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), priceTargets)
    }

    @Test
    fun `switching period cancels stale chart load and shows new period result`() = runTest {
        val monthData = IntradayChartData(
            timestamps = listOf(1000L, 2000L),
            prices = listOf(300.0, 302.0),
            previousClose = 295.0
        )
        // DAY saknas i map → getIntradayChart returnerar null → triggar 2s retry-delay
        val fake = FakeMarketDataService(
            pricesBySymbol = mapOf("VOLV-B.ST" to 300.0),
            chartDataByPeriod = mapOf(ChartPeriod.MONTH to monthData)
        )
        val viewModel = StockDetailViewModel(
            InMemoryWatchItemDao(emptyList()), fake, "VOLV-B.ST",
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
        )
        // ViewModel init har anropat loadChartData() för DAY (null → 2s retry delay pending)

        // Byt period — ska avbryta DAY-coroutinen och starta MONTH
        viewModel.selectPeriod(ChartPeriod.MONTH)

        // Ingen advanceUntilIdle() — orsakar hänge via observeAlerts() + MutableStateFlow-loop.
        // Med UnconfinedTestDispatcher körs MONTH-coroutinen eagerly och är klar direkt.
        assertTrue(
            "Expected Success but got ${viewModel.chartState.value}",
            viewModel.chartState.value is UiState.Success
        )
    }

    @Test
    fun `toggleAllAlerts sets isActive for all watch items of the symbol only`() = runTest {
        val symbol = "VOLV-B.ST"
        val otherSymbol = "AAPL"
        val watchItems = listOf(
            WatchItem(id = 1, watchType = WatchType.PriceTarget(100.0, WatchType.PriceDirection.ABOVE), ticker = symbol, isActive = true),
            WatchItem(id = 2, watchType = WatchType.PriceTarget(200.0, WatchType.PriceDirection.ABOVE), ticker = symbol, isActive = false),
            WatchItem(id = 3, watchType = WatchType.PriceTarget(50.0, WatchType.PriceDirection.ABOVE), ticker = otherSymbol, isActive = true)
        )
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(watchItems)
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(symbol to 150.0),
            previousCloseBySymbol = mapOf(symbol to 150.0)
        )
        val viewModel = StockDetailViewModel(
            watchItemDao, marketDataService, symbol,
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
        )
        advanceUntilIdle()

        viewModel.toggleAllAlerts(false)
        advanceUntilIdle()

        val allItems = watchItemDao.getAllWatchItems()
        assertTrue(allItems.filter { it.ticker == symbol }.none { it.isActive })
        assertTrue(allItems.first { it.ticker == otherSymbol }.isActive)
    }

    @Test
    fun `reactivateAlertAndReturnResult keeps lastTriggeredDate lock for DailyMove triggered today`() = runTest {
        val symbol = "VOLV-B.ST"
        val today = WatchItem.getTodayDateString()
        val triggeredItem = WatchItem(
            id = 1,
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = symbol,
            isActive = true,
            isTriggered = true,
            lastTriggeredDate = today
        )
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(listOf(triggeredItem))
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(symbol to 300.0)
        )
        val viewModel = StockDetailViewModel(
            watchItemDao, marketDataService, symbol,
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
        )
        // Ingen advanceUntilIdle() — se CLAUDE.md: observeAlerts() håller en oändlig loop.
        // reactivateAlertAndReturnResult är ett direkt suspend-anrop som körs klart synkront
        // under UnconfinedTestDispatcher eftersom DailyMove-vägen inte gör någon riktig
        // suspension (nätverksanrop hoppas över helt av ReactivationGuard för icke-
        // strukturellt justerade typer).
        viewModel.reactivateAlertAndReturnResult(triggeredItem)

        val updated = watchItemDao.getWatchItemById(1)!!
        assertTrue(updated.isActive)
        assertEquals(false, updated.isTriggered)
        assertEquals(today, updated.lastTriggeredDate)
    }

    @Test
    fun `updateWatchItem keeps lastTriggeredDate lock for DailyMove triggered today`() = runTest {
        val symbol = "VOLV-B.ST"
        val today = WatchItem.getTodayDateString()
        val triggeredItem = WatchItem(
            id = 1,
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = symbol,
            isActive = true,
            isTriggered = true,
            lastTriggeredDate = today
        )
        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(listOf(triggeredItem))
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(symbol to 300.0)
        )
        val viewModel = StockDetailViewModel(
            watchItemDao, marketDataService, symbol,
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
        )
        // Ingen advanceUntilIdle() — se motivering i föregående test.
        viewModel.updateWatchItem(triggeredItem)

        val updated = watchItemDao.getWatchItemById(1)!!
        assertTrue(updated.isActive)
        assertEquals(false, updated.isTriggered)
        assertEquals(today, updated.lastTriggeredDate)
    }

    // --- Laddning, fel och omförsök för aktiedata ---
    // Ingen advanceUntilIdle() i dessa tester (se CLAUDE.md); advanceTimeBy/runCurrent är begränsade.

    private fun snapshot(price: Double): StockDetailSnapshot = StockDetailSnapshot(
        lastPrice = price,
        previousClose = null,
        dailyChangePercent = null,
        week52High = null,
        week52Low = null,
        currency = "SEK",
        exchangeName = "STO",
        companyName = "Volvo B"
    )

    private fun createViewModel(service: MarketDataService, symbol: String = "VOLV-B.ST") = StockDetailViewModel(
        InMemoryWatchItemDao(emptyList()), service, symbol,
        TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
        com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
    )

    @Test
    fun `first load without snapshot emits Error`() = runTest {
        val viewModel = createViewModel(FakeMarketDataService())
        runCurrent()

        assertTrue(viewModel.stockDataState.value is UiState.Error)
    }

    @Test
    fun `failed refresh keeps previous data and signals refreshFailed`() = runTest {
        var fail = false
        val service = FakeMarketDataService(snapshotProvider = {
            if (fail) throw IOException("offline") else snapshot(300.0)
        })
        val viewModel = createViewModel(service)
        runCurrent()
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.refreshFailed.collect { events.add(it) }
        }

        fail = true
        viewModel.loadStockData()
        runCurrent()

        val state = viewModel.stockDataState.value
        assertTrue(state is UiState.Success)
        assertEquals(300.0, (state as UiState.Success).data.lastPrice!!, 0.0001)
        assertEquals(1, events.size)
    }

    @Test
    fun `retry after failed first load emits Success`() = runTest {
        var fail = true
        val service = FakeMarketDataService(snapshotProvider = {
            if (fail) null else snapshot(310.0)
        })
        val viewModel = createViewModel(service)
        runCurrent()
        assertTrue(viewModel.stockDataState.value is UiState.Error)

        fail = false
        viewModel.refresh()
        runCurrent()

        val state = viewModel.stockDataState.value
        assertTrue(state is UiState.Success)
        assertEquals(310.0, (state as UiState.Success).data.lastPrice!!, 0.0001)
    }

    @Test
    fun `stale slow load cannot overwrite newer successful load`() = runTest {
        var calls = 0
        val service = FakeMarketDataService(snapshotProvider = {
            calls++
            if (calls == 1) {
                // Första (äldre) anropet är långsamt och misslyckas.
                delay(1_000L)
                throw IOException("timeout")
            }
            snapshot(320.0)
        })
        val viewModel = createViewModel(service) // init startar anrop 1
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.refreshFailed.collect { events.add(it) }
        }

        viewModel.loadStockData() // anrop 2, ska avbryta anrop 1
        advanceTimeBy(2_000L)
        runCurrent()

        val state = viewModel.stockDataState.value
        assertTrue(state is UiState.Success)
        assertEquals(320.0, (state as UiState.Success).data.lastPrice!!, 0.0001)
        assertTrue(events.isEmpty())
    }
}
