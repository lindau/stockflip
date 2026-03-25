package com.stockflip

import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryStockNoteDao
import com.stockflip.testutil.InMemoryTriggerHistoryDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao())
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
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao())
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
    fun `switching period cancels stale chart load and shows new period result`() = runTest {
        val monthData = IntradayChartData(
            timestamps = listOf(1000L, 2000L),
            prices = listOf(300.0, 302.0),
            previousClose = 295.0
        )
        // DAY saknas i map → getIntradayChart returnerar null → triggar retry-delay på 2s
        val fake = FakeMarketDataService(
            pricesBySymbol = mapOf("VOLV-B.ST" to 300.0),
            chartDataByPeriod = mapOf(ChartPeriod.MONTH to monthData)
        )
        val viewModel = StockDetailViewModel(
            InMemoryWatchItemDao(emptyList()), fake, "VOLV-B.ST",
            TriggerHistoryRepository(InMemoryTriggerHistoryDao()), InMemoryStockNoteDao()
        )

        // Starta DAY-hämtning (returnerar null → påbörjar 2s retry-delay)
        viewModel.loadChartData()
        advanceTimeBy(500) // Inne i delay — hämtning ej klar

        // Byt period — ska avbryta DAY-coroutinen och starta MONTH
        viewModel.selectPeriod(ChartPeriod.MONTH)
        advanceUntilIdle() // MONTH-coroutinen körs klart

        // Utan fix: DAY-coroutinen vaknar efter delay → sätter Error → grafen försvinner
        // Med fix: DAY-coroutinen är avbruten → MONTH-Success gäller
        assertTrue(
            "Expected Success but got ${viewModel.chartState.value}",
            viewModel.chartState.value is UiState.Success
        )
    }
}

