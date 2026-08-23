package com.stockflip

import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryStockNoteDao
import com.stockflip.testutil.InMemoryStockPairDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
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

        val viewModel = MainViewModel(stockPairDao, watchItemDao, marketDataService, InMemoryStockNoteDao())
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

    @Test
    fun `loadWatchItems force show stale data emits success for key metrics`() = runBlocking {
        val watchItems: List<WatchItem> = listOf(
            WatchItem(
                id = 1,
                watchType = WatchType.KeyMetrics(
                    metricType = WatchType.MetricType.PE_RATIO,
                    targetValue = 15.0,
                    direction = WatchType.PriceDirection.BELOW
                ),
                ticker = "VOLV-B.ST",
                companyName = "Volvo B"
            )
        )

        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(watchItems)
        val stockPairDao: StockPairDao = InMemoryStockPairDao(emptyList())
        val marketDataService: MarketDataService = FakeMarketDataService()

        val viewModel = MainViewModel(stockPairDao, watchItemDao, marketDataService, InMemoryStockNoteDao())
        viewModel.loadWatchItems(forceShowStaleData = true)

        val state: UiState<List<WatchItemUiState>> = viewModel.watchItemUiState.value
        val success: UiState.Success<List<WatchItemUiState>> = state as UiState.Success<List<WatchItemUiState>>
        assertEquals(1, success.data.size)
        assertEquals(1, success.data.first().item.id)
    }

    @Test
    fun `toggleWatchItemActive only changes active flag`() = runBlocking {
        val triggeredItem = WatchItem(
            id = 1,
            watchType = WatchType.PriceTarget(targetPrice = 250.0, direction = WatchType.PriceDirection.ABOVE),
            ticker = "VOLV-B.ST",
            companyName = "Volvo B",
            isActive = true,
            isTriggered = true,
            lastTriggeredDate = "2026-04-30"
        )
        val watchItemDao = InMemoryWatchItemDao(listOf(triggeredItem))
        val viewModel = MainViewModel(
            stockPairDao = InMemoryStockPairDao(emptyList()),
            watchItemDao = watchItemDao,
            yahooFinanceService = FakeMarketDataService(),
            stockNoteDao = InMemoryStockNoteDao()
        )

        viewModel.toggleWatchItemActive(triggeredItem, false)

        val updated = watchItemDao.getWatchItemById(1)!!
        assertFalse(updated.isActive)
        assertTrue(updated.isTriggered)
        assertEquals("2026-04-30", updated.lastTriggeredDate)
    }

    @Test
    fun `reactivateWatchItem recalculates price target direction from current price`() = runBlocking {
        val triggeredItem = WatchItem(
            id = 1,
            watchType = WatchType.PriceTarget(targetPrice = 100.0, direction = WatchType.PriceDirection.ABOVE),
            ticker = "VOLV-B.ST",
            companyName = "Volvo B",
            isActive = false,
            isTriggered = true,
            lastTriggeredDate = "2026-04-30"
        )
        val watchItemDao = InMemoryWatchItemDao(listOf(triggeredItem))
        val viewModel = MainViewModel(
            stockPairDao = InMemoryStockPairDao(emptyList()),
            watchItemDao = watchItemDao,
            yahooFinanceService = FakeMarketDataService(pricesBySymbol = mapOf("VOLV-B.ST" to 105.0)),
            stockNoteDao = InMemoryStockNoteDao()
        )

        viewModel.reactivateWatchItem(triggeredItem)

        val updated = watchItemDao.getWatchItemById(1)!!
        val updatedType = updated.watchType as WatchType.PriceTarget
        assertTrue(updated.isActive)
        assertFalse(updated.isTriggered)
        assertEquals(null, updated.lastTriggeredDate)
        assertEquals(WatchType.PriceDirection.BELOW, updatedType.direction)
    }

    @Test
    fun `importData replaces existing watch items and stock pairs`() = runBlocking {
        val existingWatchItem = WatchItem(
            id = 1,
            watchType = WatchType.PriceTarget(targetPrice = 50.0, direction = WatchType.PriceDirection.ABOVE),
            ticker = "OLD.ST",
            companyName = "Old"
        )
        val existingStockPair = StockPair(
            id = 1,
            ticker1 = "OLD-A.ST",
            ticker2 = "OLD-B.ST",
            companyName1 = "Old A",
            companyName2 = "Old B"
        )
        val watchItemDao = InMemoryWatchItemDao(listOf(existingWatchItem))
        val stockPairDao = InMemoryStockPairDao(listOf(existingStockPair))
        val viewModel = MainViewModel(
            stockPairDao = stockPairDao,
            watchItemDao = watchItemDao,
            yahooFinanceService = FakeMarketDataService(),
            stockNoteDao = InMemoryStockNoteDao()
        )

        val result = viewModel.importData(importBackupJson())

        assertTrue(result.toString(), result is MainViewModel.ImportResult.Success)
        val success = result as MainViewModel.ImportResult.Success
        assertEquals(1, success.watchCount)
        assertEquals(1, success.pairCount)
        assertEquals(listOf("NEW.ST"), watchItemDao.getAllWatchItems().map { it.ticker })
        assertEquals(listOf("NEW-A.ST"), stockPairDao.getAllStockPairs().map { it.ticker1 })
    }

    @Test
    fun `syncAfterImport shows imported key metrics items immediately`() = runBlocking {
        val watchItems: List<WatchItem> = listOf(
            WatchItem(
                id = 1,
                watchType = WatchType.KeyMetrics(
                    metricType = WatchType.MetricType.PE_RATIO,
                    targetValue = 15.0,
                    direction = WatchType.PriceDirection.BELOW
                ),
                ticker = "VOLV-B.ST",
                companyName = "Volvo B"
            )
        )

        val watchItemDao: WatchItemDao = InMemoryWatchItemDao(watchItems)
        val stockPairDao: StockPairDao = InMemoryStockPairDao(emptyList())
        val marketDataService: MarketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf("VOLV-B.ST" to 300.0)
        )

        val viewModel = MainViewModel(stockPairDao, watchItemDao, marketDataService, InMemoryStockNoteDao())

        viewModel.syncAfterImport()

        val state: UiState<List<WatchItemUiState>> = viewModel.watchItemUiState.value
        val success: UiState.Success<List<WatchItemUiState>> = state as UiState.Success<List<WatchItemUiState>>
        assertEquals(1, success.data.size)
        assertEquals(300.0, success.data.first().live.currentPrice, 0.0001)
    }

    private fun importBackupJson(): String {
        return """
            {
              "version": 1,
              "exportedAt": "2026-04-30T12:00:00",
              "watchItems": [
                {
                  "watchType": "PRICE_TARGET|100.0|BELOW",
                  "ticker": "NEW.ST",
                  "companyName": "New",
                  "ticker1": null,
                  "ticker2": null,
                  "companyName1": null,
                  "companyName2": null,
                  "lastTriggeredDate": null,
                  "isTriggered": false,
                  "isActive": true
                }
              ],
              "stockPairs": [
                {
                  "ticker1": "NEW-A.ST",
                  "ticker2": "NEW-B.ST",
                  "companyName1": "New A",
                  "companyName2": "New B",
                  "priceDifference": 5.0,
                  "notifyWhenEqual": true
                }
              ]
            }
        """.trimIndent()
    }
}
