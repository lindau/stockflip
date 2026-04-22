package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.backup.BackupManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainViewModel(
    private val stockPairDao: StockPairDao,
    private val watchItemDao: WatchItemDao,
    private val yahooFinanceService: MarketDataService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<StockPair>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<StockPair>>> = _uiState

    private val _watchItemUiState = MutableStateFlow<UiState<List<WatchItemUiState>>>(UiState.Loading)
    val watchItemUiState: StateFlow<UiState<List<WatchItemUiState>>> = _watchItemUiState.asStateFlow()

    private val _alertSortMode = MutableStateFlow(SortHelper.SortMode.ADDITION_ORDER)
    val alertSortMode: StateFlow<SortHelper.SortMode> = _alertSortMode.asStateFlow()

    private val _pairsSortMode = MutableStateFlow(SortHelper.SortMode.ADDITION_ORDER)
    val pairsSortMode: StateFlow<SortHelper.SortMode> = _pairsSortMode.asStateFlow()

    private var isRefreshing = false

    init {
        Log.d(TAG, "MainViewModel initialized")
        viewModelScope.launch {
            loadStockPairs()
        }
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(120_000)
                try {
                    refreshStockPairs()
                    refreshWatchItems()
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-refresh failed: ${e.message}")
                }
            }
        }
    }

    suspend fun loadStockPairs() {
        try {
            Log.d(TAG, "Loading stock pairs from database")
            _uiState.value = UiState.Loading
            val pairs = stockPairDao.getAllStockPairs()
            Log.d(TAG, "Loaded ${pairs.size} stock pairs")
            _uiState.value = UiState.Success(pairs)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stock pairs: ${e.message}")
            _uiState.value = UiState.Error("Failed to load stock pairs: ${e.message}")
        }
    }

    suspend fun refreshStockPairs() {
        try {
            Log.d(TAG, "Refreshing stock pairs")
            _uiState.value = UiState.Loading
            
            val pairs = stockPairDao.getAllStockPairs()
            Log.d(TAG, "Found ${pairs.size} pairs to refresh")
            
            val semaphore = Semaphore(4)
            val updatedPairs = coroutineScope {
                pairs.map { pair ->
                    async {
                        semaphore.withPermit {
                            try {
                                Log.d(TAG, "Fetching prices for ${pair.ticker1} and ${pair.ticker2}")
                                val price1 = yahooFinanceService.getStockPrice(pair.ticker1)
                                val price2 = yahooFinanceService.getStockPrice(pair.ticker2)

                                if (price1 != null && price2 != null) {
                                    Log.d(TAG, "Got prices for ${pair.ticker1}: $price1, ${pair.ticker2}: $price2")
                                    val updatedPair = pair.withCurrentPrices(price1, price2)
                                    stockPairDao.update(updatedPair)
                                    Log.d(TAG, "Updated database with new prices for ${pair.ticker1}-${pair.ticker2}")
                                    updatedPair
                                } else {
                                    Log.w(TAG, "Could not get prices for ${pair.ticker1} or ${pair.ticker2}, keeping existing prices")
                                    pair
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching prices for ${pair.ticker1}-${pair.ticker2}: ${e.message}")
                                pair
                            }
                        }
                    }
                }.awaitAll()
            }
            
            _uiState.value = UiState.Success(updatedPairs)
            Log.d(TAG, "Successfully refreshed ${updatedPairs.size} stock pairs")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing stock pairs: ${e.message}")
            _uiState.value = UiState.Error("Failed to refresh stock pairs: ${e.message}")
        }
    }

    suspend fun addStockPair(stockPair: StockPair) {
        try {
            Log.d(TAG, "Adding new stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            stockPairDao.insertStockPair(stockPair)
            refreshStockPairs() // Immediately refresh prices after adding
        } catch (e: Exception) {
            Log.e(TAG, "Error adding stock pair: ${e.message}")
            _uiState.value = UiState.Error("Failed to add stock pair: ${e.message}")
        }
    }

    suspend fun deleteStockPair(stockPair: StockPair) {
        try {
            Log.d(TAG, "Deleting stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            stockPairDao.deleteStockPair(stockPair)
            loadStockPairs() // Reload the list after deleting
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting stock pair: ${e.message}")
            _uiState.value = UiState.Error("Failed to delete stock pair: ${e.message}")
        }
    }

    suspend fun updateStockPair(stockPair: StockPair) {
        try {
            Log.d(TAG, "Updating stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            stockPairDao.update(stockPair)
            loadStockPairs() // Reload the list after updating
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stock pair: ${e.message}")
            _uiState.value = UiState.Error("Failed to update stock pair: ${e.message}")
        }
    }

    suspend fun loadWatchItems() {
        loadWatchItems(forceShowStaleData = false)
    }

    suspend fun loadWatchItems(forceShowStaleData: Boolean) {
        try {
            Log.d(TAG, "Loading watch items from database")
            _watchItemUiState.value = UiState.Loading
            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Loaded ${items.size} watch items")
            
            // Check if there are KeyMetrics items that need refreshing
            // KeyMetrics currentMetricValue is @Ignore and not saved to database,
            // so we need to refresh to get the actual values
            val hasKeyMetrics = items.any { it.watchType is WatchType.KeyMetrics }
            
            if (hasKeyMetrics && !forceShowStaleData) {
                Log.d(TAG, "Found KeyMetrics items, keeping Loading state until refresh completes")
                // Don't set Success state yet - wait for refreshWatchItems() to complete
                // This ensures KeyMetrics values are loaded before showing UI
            } else {
                // No KeyMetrics items, safe to show data from database immediately
                _watchItemUiState.value = UiState.Success(items.map { WatchItemUiState(it) })
                Log.d(TAG, "No KeyMetrics items, set UI state to Success")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading watch items: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to load watch items: ${e.message}")
        }
    }

    suspend fun refreshWatchItems(showLoading: Boolean = true) {
        // Prevent concurrent refresh calls
        if (isRefreshing) {
            Log.d(TAG, "Refresh already in progress, skipping duplicate call")
            return
        }
        
        isRefreshing = true
        try {
            Log.d(TAG, "=== START refreshWatchItems() ===")
            if (showLoading) {
                _watchItemUiState.value = UiState.Loading
                Log.d(TAG, "Set UI state to Loading")
            }

            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Found ${items.size} watch items to refresh")

            // Parallellisera med max 4 samtida anrop för att undvika rate limiting
            val semaphore = Semaphore(4)
            val updatedItems = coroutineScope {
            items.map { item ->
                async {
                semaphore.withPermit {
                val now = System.currentTimeMillis()
                try {
                    when (item.watchType) {
                        is WatchType.PricePair -> {
                            if (item.ticker1 != null && item.ticker2 != null) {
                                Log.d(TAG, "Fetching prices for ${item.ticker1} and ${item.ticker2}")
                                val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                                val price2 = yahooFinanceService.getStockPrice(item.ticker2)
                                if (price1 != null && price2 != null) {
                                    Log.d(TAG, "Got prices for ${item.ticker1}: $price1, ${item.ticker2}: $price2")
                                    WatchItemUiState(item, LiveWatchData(currentPrice1 = price1, currentPrice2 = price2, lastUpdatedAt = now))
                                } else {
                                    Log.w(TAG, "Could not get prices for ${item.ticker1} or ${item.ticker2}")
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.PriceTarget -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price and daily change for ${item.ticker}")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                if (price != null) {
                                    Log.d(TAG, "Got price for ${item.ticker}: $price, changePercent: $changePercent")
                                    WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                                } else {
                                    Log.w(TAG, "Could not get price for ${item.ticker}")
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.KeyMetrics -> {
                            if (item.ticker != null) {
                                val keyMetrics = item.watchType
                                Log.d(TAG, "Fetching key metric ${keyMetrics.metricType.name} and price for ${item.ticker}")
                                try {
                                    val metricValue = yahooFinanceService.getKeyMetric(item.ticker, keyMetrics.metricType)
                                    val price = yahooFinanceService.getStockPrice(item.ticker)
                                    val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                    Log.d(TAG, "getKeyMetric returned: $metricValue for ${item.ticker}")
                                    if (metricValue != null) {
                                        Log.d(TAG, "Got metric value for ${item.ticker}: $metricValue")
                                        WatchItemUiState(item, LiveWatchData(
                                            currentMetricValue = metricValue,
                                            metricValueAtCreation = metricValue,
                                            currentPrice = price ?: 0.0,
                                            currentDailyChangePercent = changePercent,
                                            lastUpdatedAt = now
                                        ))
                                    } else if (price != null) {
                                        WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                                    } else {
                                        Log.w(TAG, "Could not get metric value or price for ${item.ticker}")
                                        WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Exception while fetching key metric for ${item.ticker}: ${e.message}", e)
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                Log.w(TAG, "Ticker is null for KeyMetrics watch item ${item.id}")
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.ATHBased -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching ATH, price and daily change for ${item.ticker}")
                                val ath = yahooFinanceService.getATH(item.ticker)
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                if (ath != null && price != null) {
                                    Log.d(TAG, "Got ATH for ${item.ticker}: $ath, Price: $price, changePercent: $changePercent")
                                    WatchItemUiState(item, LiveWatchData(
                                        currentATH = ath,
                                        currentPrice = price,
                                        currentDropPercentage = ((ath - price) / ath) * 100,
                                        currentDropAbsolute = ath - price,
                                        currentDailyChangePercent = changePercent,
                                        lastUpdatedAt = now
                                    ))
                                } else {
                                    Log.w(TAG, "Could not get ATH or price for ${item.ticker}")
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.PriceRange -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price and daily change for ${item.ticker} (PriceRange)")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                if (price != null) {
                                    WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                                } else {
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.DailyMove -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price and daily change for ${item.ticker} (DailyMove)")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                if (price != null) {
                                    WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                                } else {
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                WatchItemUiState(item)
                            }
                        }
                        is WatchType.Combined -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price and daily change for combined alert ticker: ${item.ticker}")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                                if (price != null) {
                                    WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                                } else {
                                    Log.w(TAG, "Could not get price for combined alert ticker ${item.ticker}")
                                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                                }
                            } else {
                                Log.w(TAG, "Combined alert has no ticker set")
                                WatchItemUiState(item)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching prices for watch item ${item.id}: ${e.message}")
                    WatchItemUiState(item, LiveWatchData(updateFailed = true))
                }
                } // semaphore.withPermit
                } // async
            }.awaitAll()
            } // coroutineScope

            Log.d(TAG, "Refresh complete, built ${updatedItems.size} WatchItemUiState objects")

            // Update StateFlow
            try {
                Log.d(TAG, "About to update StateFlow with ${updatedItems.size} items")
                _watchItemUiState.value = UiState.Success(updatedItems)
                Log.d(TAG, "Set UI state to Success with ${updatedItems.size} watch items")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Error updating StateFlow: ${e.message}", e)
                throw e
            }
            Log.d(TAG, "=== END refreshWatchItems() - SUCCESS ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== END refreshWatchItems() - ERROR: ${e.message} ===", e)
            if (showLoading) {
                _watchItemUiState.value = UiState.Error("Failed to refresh watch items: ${e.message}")
            }
            Log.d(TAG, "Set UI state to Error")
        } finally {
            isRefreshing = false
            Log.d(TAG, "Refresh flag reset")
        }
    }

    suspend fun addWatchItem(watchItem: WatchItem) {
        try {
            Log.d(TAG, "Adding new watch item: ${watchItem.getDisplayName()}")
            watchItemDao.insertWatchItem(watchItem)
            syncWatchItemsAfterMutation()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to add watch item: ${e.message}")
        }
    }

    suspend fun deleteStockBySymbol(symbol: String) {
        try {
            Log.d(TAG, "Deleting all watches for symbol: $symbol")
            watchItemDao.deleteBySymbol(symbol)
            syncWatchItemsAfterMutation()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watches for $symbol: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to delete watches: ${e.message}")
        }
    }

    suspend fun deleteWatchItem(watchItem: WatchItem) {
        try {
            Log.d(TAG, "Deleting watch item: ${watchItem.getDisplayName()}")
            watchItemDao.deleteWatchItem(watchItem)
            syncWatchItemsAfterMutation()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to delete watch item: ${e.message}")
        }
    }

    fun setAlertSortMode(mode: SortHelper.SortMode) {
        _alertSortMode.value = mode
    }

    fun setPairsSortMode(mode: SortHelper.SortMode) {
        _pairsSortMode.value = mode
    }

    suspend fun toggleWatchItemActive(watchItem: WatchItem, isActive: Boolean) {
        val updatedWatchItem: WatchItem = watchItem.setActive(isActive)
        updateWatchItem(updatedWatchItem)
    }

    suspend fun reactivateWatchItem(watchItem: WatchItem) {
        val updatedWatchItem: WatchItem = watchItem.reactivate()
        updateWatchItem(updatedWatchItem)
    }

    suspend fun updateWatchItem(watchItem: WatchItem) {
        try {
            Log.d(TAG, "Updating watch item: ${watchItem.getDisplayName()}")
            watchItemDao.update(watchItem.reactivate())
            syncWatchItemsAfterMutation()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to update watch item: ${e.message}")
        }
    }

    private suspend fun syncWatchItemsAfterMutation() {
        // Reflect structural DB changes immediately, then refresh live prices in the background.
        loadWatchItems(forceShowStaleData = true)
        viewModelScope.launch {
            refreshWatchItems(showLoading = false)
        }
    }

    suspend fun exportData(): String {
        val watchItems = watchItemDao.getAllWatchItems()
        val stockPairs = stockPairDao.getAllStockPairs()
        return BackupManager.exportToJson(watchItems, stockPairs)
    }

    suspend fun importData(json: String): ImportResult {
        return try {
            val data = BackupManager.importFromJson(json)
            data.watchItems.forEach { watchItemDao.insertWatchItem(it) }
            data.stockPairs.forEach { stockPairDao.insertStockPair(it) }
            ImportResult.Success(data.watchItems.size, data.stockPairs.size)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Okänt fel")
        }
    }

    sealed class ImportResult {
        data class Success(val watchCount: Int, val pairCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
} 
