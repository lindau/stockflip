package com.stockflip

import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryStockPairDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelRefreshWatchItemsTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refreshWatchItems updates prices for PriceTarget, PricePair and Combined`() = runBlocking {
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
        val state: UiState<List<WatchItemUiState>> = viewModel.watchItemUiState.value
        val success: UiState.Success<List<WatchItemUiState>> = state as UiState.Success<List<WatchItemUiState>>
        val updated: List<WatchItemUiState> = success.data
        val updatedTarget = updated.first { it.item.id == 1 }
        val updatedPair = updated.first { it.item.id == 2 }
        val updatedCombined = updated.first { it.item.id == 3 }
        assertEquals(300.0, updatedTarget.live.currentPrice, 0.0001)
        assertEquals(300.0, updatedPair.live.currentPrice1, 0.0001)
        assertEquals(280.0, updatedPair.live.currentPrice2, 0.0001)
        assertEquals(300.0, updatedCombined.live.currentPrice, 0.0001)
    }
}

