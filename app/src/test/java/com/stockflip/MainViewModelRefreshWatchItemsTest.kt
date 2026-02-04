package com.stockflip

import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryStockPairDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelRefreshWatchItemsTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refreshWatchItems updates prices for PriceTarget, PricePair and Combined`() = runTest {
        val watchItems: List<WatchItem> = listOf(
            WatchItem(
                id = 1,
                watchType = WatchType.PriceTarget(targetPrice = 250.0, direction = WatchType.PriceDirection.ABOVE),
                ticker = "VOLV-B.ST",
                companyName = "Volvo B"
            ),
            WatchItem(
                id = 2,
                watchType = WatchType.PricePair(priceDifference = 10.0, notifyWhenEqual = false),
                ticker1 = "VOLV-B.ST",
                ticker2 = "ASSA-B.ST",
                companyName1 = "Volvo B",
                companyName2 = "Assa Abloy B"
            ),
            WatchItem(
                id = 3,
                watchType = WatchType.Combined(
                    AlertExpression.Single(
                        AlertRule.SinglePrice("VOLV-B.ST", AlertRule.PriceComparisonType.ABOVE, 200.0)
                    )
                ),
                ticker = "VOLV-B.ST",
                companyName = "Volvo B"
            )
        )

        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(watchItems)
        val stockPairDao: StockPairDao = InMemoryStockPairDao(emptyList())
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(
                "VOLV-B.ST" to 300.0,
                "ASSA-B.ST" to 280.0
            )
        )

        val viewModel = MainViewModel(stockPairDao, watchItemDao, marketDataService)
        viewModel.refreshWatchItems()
        val state: UiState<List<WatchItem>> = viewModel.watchItemUiState.value
        val success: UiState.Success<List<WatchItem>> = state as UiState.Success<List<WatchItem>>
        val updated: List<WatchItem> = success.data
        val updatedTarget: WatchItem = updated.first { it.id == 1 }
        val updatedPair: WatchItem = updated.first { it.id == 2 }
        val updatedCombined: WatchItem = updated.first { it.id == 3 }
        assertEquals(300.0, updatedTarget.currentPrice, 0.0001)
        assertEquals(300.0, updatedPair.currentPrice1, 0.0001)
        assertEquals(280.0, updatedPair.currentPrice2, 0.0001)
        assertEquals(300.0, updatedCombined.currentPrice, 0.0001)
    }
}

