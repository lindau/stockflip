package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val stockPairDao: StockPairDao,
    private val watchItemDao: WatchItemDao,
    private val yahooFinanceService: YahooFinanceService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<StockPair>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<StockPair>>> = _uiState

    private val _watchItemUiState = MutableStateFlow<UiState<List<WatchItem>>>(UiState.Loading)
    val watchItemUiState: StateFlow<UiState<List<WatchItem>>> = _watchItemUiState.asStateFlow()
    
    private var isRefreshing = false

    init {
        Log.d(TAG, "MainViewModel initialized")
        viewModelScope.launch {
            loadStockPairs()
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
            
            val updatedPairs = pairs.map { pair ->
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
        try {
            Log.d(TAG, "Loading watch items from database")
            _watchItemUiState.value = UiState.Loading
            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Loaded ${items.size} watch items")
            
            // Check if there are KeyMetrics items that need refreshing
            // KeyMetrics currentMetricValue is @Ignore and not saved to database,
            // so we need to refresh to get the actual values
            val hasKeyMetrics = items.any { it.watchType is WatchType.KeyMetrics }
            
            if (hasKeyMetrics) {
                Log.d(TAG, "Found KeyMetrics items, keeping Loading state until refresh completes")
                // Don't set Success state yet - wait for refreshWatchItems() to complete
                // This ensures KeyMetrics values are loaded before showing UI
            } else {
                // No KeyMetrics items, safe to show data from database immediately
                _watchItemUiState.value = UiState.Success(items)
                Log.d(TAG, "No KeyMetrics items, set UI state to Success")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading watch items: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to load watch items: ${e.message}")
        }
    }

    suspend fun refreshWatchItems() {
        // Prevent concurrent refresh calls
        if (isRefreshing) {
            Log.d(TAG, "Refresh already in progress, skipping duplicate call")
            return
        }
        
        isRefreshing = true
        try {
            Log.d(TAG, "=== START refreshWatchItems() ===")
            _watchItemUiState.value = UiState.Loading
            Log.d(TAG, "Set UI state to Loading")

            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Found ${items.size} watch items to refresh")

            // Process items sequentially to avoid rate limits
            val updatedItems = items.mapIndexed { index, item ->
                try {
                    // Add small delay between key metric requests to avoid rate limits
                    if (item.watchType is WatchType.KeyMetrics && index > 0) {
                        delay(1000) // 1 second delay between key metric requests
                    }
                    
                    when (item.watchType) {
                        is WatchType.PricePair -> {
                            if (item.ticker1 != null && item.ticker2 != null) {
                                Log.d(TAG, "Fetching prices for ${item.ticker1} and ${item.ticker2}")
                                val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                                val price2 = yahooFinanceService.getStockPrice(item.ticker2)

                                if (price1 != null && price2 != null) {
                                    Log.d(TAG, "Got prices for ${item.ticker1}: $price1, ${item.ticker2}: $price2")
                                    val updatedItem = item.withCurrentPrices(price1, price2)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new prices for ${item.ticker1}-${item.ticker2}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get prices for ${item.ticker1} or ${item.ticker2}, keeping existing prices")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.PriceTarget -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price for ${item.ticker}")
                                val price = yahooFinanceService.getStockPrice(item.ticker)

                                if (price != null) {
                                    Log.d(TAG, "Got price for ${item.ticker}: $price")
                                    val updatedItem = item.withCurrentPrice(price)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new price for ${item.ticker}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get price for ${item.ticker}, keeping existing price")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.KeyMetrics -> {
                            if (item.ticker != null) {
                                val keyMetrics = item.watchType
                                Log.d(TAG, "Fetching key metric ${keyMetrics.metricType.name} for ${item.ticker}")
                                try {
                                    val metricValue = yahooFinanceService.getKeyMetric(item.ticker, keyMetrics.metricType)
                                    Log.d(TAG, "getKeyMetric returned: $metricValue for ${item.ticker}")

                                    if (metricValue != null) {
                                        Log.d(TAG, "Got metric value for ${item.ticker}: $metricValue")
                                        val updatedItem = item.withCurrentMetricValue(metricValue)
                                        watchItemDao.update(updatedItem)
                                        Log.d(TAG, "Updated database with new metric value for ${item.ticker}: ${updatedItem.currentMetricValue}")
                                        updatedItem
                                    } else {
                                        Log.w(TAG, "Could not get metric value for ${item.ticker} (returned null), keeping existing value: ${item.currentMetricValue}")
                                        item
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Exception while fetching key metric for ${item.ticker}: ${e.message}", e)
                                    item
                                }
                            } else {
                                Log.w(TAG, "Ticker is null for KeyMetrics watch item ${item.id}")
                                item
                            }
                        }
                        is WatchType.ATHBased -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching ATH and price for ${item.ticker}")
                                val ath = yahooFinanceService.getATH(item.ticker)
                                val price = yahooFinanceService.getStockPrice(item.ticker)

                                if (ath != null && price != null) {
                                    Log.d(TAG, "Got ATH for ${item.ticker}: $ath, Price: $price")
                                    val updatedItem = item.withATHData(ath, price)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new ATH data for ${item.ticker}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get ATH or price for ${item.ticker}, keeping existing values")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.PriceRange -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price for ${item.ticker} (PriceRange)")
                                val price = yahooFinanceService.getStockPrice(item.ticker)

                                if (price != null) {
                                    Log.d(TAG, "Got price for ${item.ticker}: $price")
                                    val updatedItem = item.withCurrentPrice(price)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new price for ${item.ticker}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get price for ${item.ticker}, keeping existing price")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.DailyMove -> {
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price and previousClose for ${item.ticker} (DailyMove)")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                val previousClose = yahooFinanceService.getPreviousClose(item.ticker)

                                if (price != null) {
                                    Log.d(TAG, "Got price for ${item.ticker}: $price, previousClose: $previousClose")
                                    val updatedItem = item.withCurrentPrice(price)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new price for ${item.ticker}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get price for ${item.ticker}, keeping existing price")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.Combined -> {
                            // Combined WatchType: använd item.ticker direkt (samma som för vanliga bevakningar)
                            if (item.ticker != null) {
                                Log.d(TAG, "Fetching price for combined alert ticker: ${item.ticker}")
                                val price = yahooFinanceService.getStockPrice(item.ticker)
                                
                                if (price != null) {
                                    Log.d(TAG, "Got price for combined alert ticker ${item.ticker}: $price")
                                    val updatedItem = item.withCurrentPrice(price)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new price for combined alert")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get price for combined alert ticker ${item.ticker}")
                                    item
                                }
                            } else {
                                Log.w(TAG, "Combined alert has no ticker set")
                                item
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching prices for watch item ${item.id}: ${e.message}")
                    item
                }
            }

            // Use updatedItems directly instead of reloading from database
            // This is important because currentMetricValue is @Ignore and not saved to database
            Log.d(TAG, "Refresh complete, using updated items directly (${updatedItems.size} items)")
            
            // Log key metrics items specifically
            try {
                val keyMetricsItems = updatedItems.filter { it.watchType is WatchType.KeyMetrics }
                Log.d(TAG, "Found ${keyMetricsItems.size} KeyMetrics items in updated list")
                keyMetricsItems.forEach { item ->
                    Log.d(TAG, "KeyMetrics item: ${item.ticker}, currentMetricValue: ${item.currentMetricValue}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error logging KeyMetrics items: ${e.message}", e)
            }
            
            // Update StateFlow - this is critical!
            try {
                Log.d(TAG, "About to update StateFlow with ${updatedItems.size} items")
                _watchItemUiState.value = UiState.Success(updatedItems)
                Log.d(TAG, "StateFlow updated! Current value type: ${_watchItemUiState.value::class.simpleName}")
                if (_watchItemUiState.value is UiState.Success) {
                    val successState = _watchItemUiState.value as UiState.Success
                    Log.d(TAG, "StateFlow contains ${successState.data.size} items")
                }
                Log.d(TAG, "Set UI state to Success with ${updatedItems.size} watch items")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Error updating StateFlow: ${e.message}", e)
                throw e
            }
            Log.d(TAG, "=== END refreshWatchItems() - SUCCESS ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== END refreshWatchItems() - ERROR: ${e.message} ===", e)
            _watchItemUiState.value = UiState.Error("Failed to refresh watch items: ${e.message}")
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
            refreshWatchItems() // Immediately refresh prices after adding
        } catch (e: Exception) {
            Log.e(TAG, "Error adding watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to add watch item: ${e.message}")
        }
    }

    suspend fun deleteWatchItem(watchItem: WatchItem) {
        try {
            Log.d(TAG, "Deleting watch item: ${watchItem.getDisplayName()}")
            watchItemDao.deleteWatchItem(watchItem)
            loadWatchItems() // Reload the list after deleting
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to delete watch item: ${e.message}")
        }
    }

    suspend fun updateWatchItem(watchItem: WatchItem) {
        try {
            Log.d(TAG, "Updating watch item: ${watchItem.getDisplayName()}")
            watchItemDao.update(watchItem)
            loadWatchItems() // Reload the list after updating
        } catch (e: Exception) {
            Log.e(TAG, "Error updating watch item: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to update watch item: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
} 