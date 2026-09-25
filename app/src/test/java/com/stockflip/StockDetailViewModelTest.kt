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
import org.junit.Assert.assertFalse
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
        assertTrue(viewModel.updateWatchItem(triggeredItem))

        val updated = watchItemDao.getWatchItemById(1)!!
        assertTrue(updated.isActive)
        assertEquals(false, updated.isTriggered)
        assertEquals(today, updated.lastTriggeredDate)
    }

    // --- Skapa/uppdatera rapporterar resultat så att dialogen bara bekräftar när det sparats ---

    /** Delegerar till InMemoryWatchItemDao men kastar vid skrivningar. */
    private class FailingWritesWatchItemDao(delegate: WatchItemDao) : WatchItemDao by delegate {
        override suspend fun insertWatchItem(item: WatchItem) = throw IllegalStateException("disk full")
        override suspend fun update(item: WatchItem) = throw IllegalStateException("disk full")
    }

    private fun detailViewModel(dao: WatchItemDao, symbol: String = "VOLV-B.ST") = StockDetailViewModel(
        dao, FakeMarketDataService(pricesBySymbol = mapOf(symbol to 300.0)), symbol,
        TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
        com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao())
    )

    @Test
    fun `createAlert returns true and stores the watch`() = runTest {
        val dao = InMemoryWatchItemDao(emptyList())
        val viewModel = detailViewModel(dao)
        // Ingen advanceUntilIdle() — se CLAUDE.md.
        val created = viewModel.createAlert(WatchType.PriceTarget(250.0, WatchType.PriceDirection.BELOW), "Volvo B")

        assertTrue(created)
        val stored = dao.getWatchItemsBySymbol("VOLV-B.ST")
        assertEquals(1, stored.size)
        assertEquals(WatchType.PriceTarget(250.0, WatchType.PriceDirection.BELOW), stored.single().watchType)
    }

    @Test
    fun `createAlert returns false when the save fails`() = runTest {
        val dao = FailingWritesWatchItemDao(InMemoryWatchItemDao(emptyList()))
        val viewModel = detailViewModel(dao)

        assertFalse(viewModel.createAlert(WatchType.DailyMove(3.0, WatchType.DailyMoveDirection.BOTH), "Volvo B"))
        assertTrue(dao.getWatchItemsBySymbol("VOLV-B.ST").isEmpty())
    }

    @Test
    fun `updateWatchItem returns false when the save fails`() = runTest {
        val item = WatchItem(
            id = 1,
            watchType = WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP),
            ticker = "VOLV-B.ST"
        )
        val dao = FailingWritesWatchItemDao(InMemoryWatchItemDao(listOf(item)))
        val viewModel = detailViewModel(dao)

        assertFalse(viewModel.updateWatchItem(item.copy(watchType = WatchType.DailyMove(7.0, WatchType.DailyMoveDirection.UP))))
        assertEquals(WatchType.DailyMove(5.0, WatchType.DailyMoveDirection.UP), dao.getWatchItemById(1)!!.watchType)
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
    fun `chart fetch failure after retry is an Error, not an empty Success`() = runTest {
        // Inga grafdata → getIntradayChart returnerar null (misslyckad hämtning) två gånger.
        val viewModel = createViewModel(FakeMarketDataService(pricesBySymbol = mapOf("VOLV-B.ST" to 300.0)))
        runCurrent()
        advanceTimeBy(2_100L)
        runCurrent()

        assertEquals(UiState.Error(CHART_LOAD_FAILED_MESSAGE), viewModel.chartState.value)
    }

    @Test
    fun `tapping the selected period after a chart error retries the load`() = runTest {
        val chartByPeriod = mutableMapOf<ChartPeriod, IntradayChartData?>()
        val viewModel = createViewModel(
            FakeMarketDataService(pricesBySymbol = mapOf("VOLV-B.ST" to 300.0), chartDataByPeriod = chartByPeriod)
        )
        runCurrent()
        advanceTimeBy(2_100L)
        runCurrent()
        assertTrue(viewModel.chartState.value is UiState.Error)

        val dayData = IntradayChartData(listOf(1L, 2L), listOf(300.0, 301.0), previousClose = 299.0)
        chartByPeriod[ChartPeriod.DAY] = dayData
        viewModel.selectPeriod(viewModel.selectedPeriod.value)
        runCurrent()

        assertEquals(UiState.Success(dayData), viewModel.chartState.value)
    }

    @Test
    fun `tapping the selected period while the chart is fine does not reload`() = runTest {
        val dayData = IntradayChartData(listOf(1L, 2L), listOf(300.0, 301.0), previousClose = 299.0)
        val viewModel = createViewModel(
            FakeMarketDataService(pricesBySymbol = mapOf("VOLV-B.ST" to 300.0), chartDataByPeriod = mapOf(ChartPeriod.DAY to dayData))
        )
        runCurrent()

        viewModel.selectPeriod(viewModel.selectedPeriod.value)

        assertEquals(UiState.Success(dayData), viewModel.chartState.value)
    }

    @Test
    fun `manual alerts refresh emits Error when the database read fails`() = runTest {
        val dao = object : WatchItemDao by InMemoryWatchItemDao(emptyList()) {
            override suspend fun getAllWatchItems(): List<WatchItem> = throw IllegalStateException("db closed")
        }
        val viewModel = detailViewModel(dao)
        runCurrent()

        viewModel.loadAlerts()
        runCurrent()

        assertEquals(UiState.Error("Kunde inte läsa in bevakningarna"), viewModel.alertsState.value)
    }

    @Test
    fun `initial quote from the list is shown before stock data has loaded and then replaced`() = runTest {
        val snapshotGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val service = FakeMarketDataService(
            pricesBySymbol = mapOf("VOLV-B.ST" to 300.0),
            snapshotProvider = { snapshotGate.await(); snapshot(305.0) }
        )
        val viewModel = StockDetailViewModel(
            InMemoryWatchItemDao(emptyList()), service, "VOLV-B.ST",
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao(),
            com.stockflip.repository.MetricHistoryRepository(InMemoryMetricHistoryDao()),
            initialQuote = InitialQuote(price = 299.5, dailyChangePercent = 1.2, companyName = "Volvo B", updatedAt = 42L)
        )
        runCurrent()

        // Innan aktiedata kommit: listans kurs visas direkt i stället för Loading.
        val preliminary = (viewModel.stockDataState.value as UiState.Success).data
        assertEquals(299.5, preliminary.lastPrice!!, 0.0)
        assertEquals(1.2, preliminary.dailyChangePercent!!, 0.0)
        assertEquals("Volvo B", preliminary.companyName)
        assertEquals("SEK", preliminary.currency)

        snapshotGate.complete(Unit)
        runCurrent()

        assertEquals(305.0, (viewModel.stockDataState.value as UiState.Success).data.lastPrice!!, 0.0)
    }

    @Test
    fun `without initial quote the first state is Loading as before`() = runTest {
        val snapshotGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val viewModel = createViewModel(FakeMarketDataService(snapshotProvider = { snapshotGate.await(); snapshot(305.0) }))
        runCurrent()

        assertEquals(UiState.Loading, viewModel.stockDataState.value)
        snapshotGate.complete(Unit)
        runCurrent()
    }

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
