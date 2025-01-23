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
    private val yahooFinanceService: YahooFinanceService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<StockPair>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<StockPair>>> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "Initializing MainViewModel")
        loadStockPairs()
    }

    private fun loadStockPairs() {
        viewModelScope.launch {
            Log.d(TAG, "Loading stock pairs from database")
            _uiState.value = UiState.Loading
            try {
                val pairs = stockPairDao.getAllStockPairs()
                Log.d(TAG, "Loaded ${pairs.size} stock pairs")
                _uiState.value = UiState.Success(pairs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stock pairs", e)
                _uiState.value = UiState.Error("Failed to load stock pairs: ${e.message}")
            }
        }
    }

    suspend fun refreshStockPairs() {
        Log.d(TAG, "Starting stock pairs refresh")
        _uiState.value = UiState.Loading
        try {
            val pairs = stockPairDao.getAllStockPairs()
            Log.d(TAG, "Found ${pairs.size} pairs to refresh")
            
            val updatedPairs = pairs.map { pair ->
                Log.d(TAG, "Fetching prices for pair: ${pair.companyName1} (${pair.ticker1}) - ${pair.companyName2} (${pair.ticker2})")
                val price1 = yahooFinanceService.getStockPrice(pair.ticker1)
                val price2 = yahooFinanceService.getStockPrice(pair.ticker2)
                Log.d(TAG, "Fetched prices: ${pair.ticker1}=$price1, ${pair.ticker2}=$price2")
                
                if (price1 != null && price2 != null) {
                    val updatedPair = pair.withCurrentPrices(price1, price2)
                    Log.d(TAG, "Updating database with new prices for ${pair.companyName1}: $price1, ${pair.companyName2}: $price2")
                    stockPairDao.update(updatedPair)
                    updatedPair
                } else {
                    Log.w(TAG, "Failed to fetch prices for ${pair.ticker1} or ${pair.ticker2}, keeping existing prices")
                    pair
                }
            }
            
            Log.d(TAG, "Updating UI with ${updatedPairs.size} refreshed pairs")
            _uiState.value = UiState.Success(updatedPairs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh stock pairs", e)
            _uiState.value = UiState.Error("Failed to refresh: ${e.message}")
        }
    }

    fun addStockPair(stockPair: StockPair) {
        viewModelScope.launch {
            Log.d(TAG, "Adding new stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            try {
                _uiState.value = UiState.Loading
                stockPairDao.insertStockPair(stockPair)
                Log.d(TAG, "Stock pair added successfully, refreshing list")
                refreshStockPairs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add stock pair", e)
                _uiState.value = UiState.Error("Failed to add stock pair: ${e.message}")
            }
        }
    }

    fun deleteStockPair(stockPair: StockPair) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            try {
                _uiState.value = UiState.Loading
                stockPairDao.deleteStockPair(stockPair)
                Log.d(TAG, "Stock pair deleted successfully, refreshing list")
                refreshStockPairs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete stock pair", e)
                _uiState.value = UiState.Error("Failed to delete stock pair: ${e.message}")
            }
        }
    }

    fun updateStockPair(stockPair: StockPair) {
        viewModelScope.launch {
            Log.d(TAG, "Updating stock pair: ${stockPair.companyName1} - ${stockPair.companyName2}")
            try {
                _uiState.value = UiState.Loading
                stockPairDao.update(stockPair)
                Log.d(TAG, "Stock pair updated successfully, refreshing list")
                refreshStockPairs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update stock pair", e)
                _uiState.value = UiState.Error("Failed to update stock pair: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
} 