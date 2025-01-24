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
    val uiState: StateFlow<UiState<List<StockPair>>> = _uiState

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
            loadStockPairs() // Reload the list after adding
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

    companion object {
        private const val TAG = "MainViewModel"
    }
} 