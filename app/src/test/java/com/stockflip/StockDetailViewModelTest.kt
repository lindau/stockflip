package com.stockflip

import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

        val viewModel = StockDetailViewModel(watchItemDao, marketDataService, "VOLV-B.ST")
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
    }
}

