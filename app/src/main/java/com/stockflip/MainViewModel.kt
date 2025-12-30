package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val watchItemUiState: StateFlow<UiState<List<WatchItem>>> = _watchItemUiState

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
            _watchItemUiState.value = UiState.Success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading watch items: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to load watch items: ${e.message}")
        }
    }

    suspend fun refreshWatchItems() {
        try {
            Log.d(TAG, "Refreshing watch items")
            _watchItemUiState.value = UiState.Loading

            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Found ${items.size} watch items to refresh")

            val updatedItems = items.map { item ->
                try {
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
                                Log.d(TAG, "Fetching key metric ${item.watchType.metricType.name} for ${item.ticker}")
                                val metricValue = yahooFinanceService.getKeyMetric(item.ticker, item.watchType.metricType)

                                if (metricValue != null) {
                                    Log.d(TAG, "Got metric value for ${item.ticker}: $metricValue")
                                    val updatedItem = item.withCurrentMetricValue(metricValue)
                                    watchItemDao.update(updatedItem)
                                    Log.d(TAG, "Updated database with new metric value for ${item.ticker}")
                                    updatedItem
                                } else {
                                    Log.w(TAG, "Could not get metric value for ${item.ticker}, keeping existing value")
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.ATHDrop -> {
                            // TODO: Handle ATH drop refresh
                            item
                        }
                        is WatchType.DailyHighDrop -> {
                            // TODO: Handle daily high drop refresh
                            item
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching prices for watch item ${item.id}: ${e.message}")
                    item
                }
            }

            _watchItemUiState.value = UiState.Success(updatedItems)
            Log.d(TAG, "Successfully refreshed ${updatedItems.size} watch items")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing watch items: ${e.message}")
            _watchItemUiState.value = UiState.Error("Failed to refresh watch items: ${e.message}")
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