package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel för aktiedetaljvy.
 * Hanterar datahämtning och alert-hantering för en enskild aktie.
 */
class StockDetailViewModel(
    private val watchItemDao: WatchItemDao,
    private val yahooFinanceService: YahooFinanceService,
    private val symbol: String
) : ViewModel() {

    private val TAG = "StockDetailViewModel"

    private val _stockDataState = MutableStateFlow<UiState<StockDetailData>>(UiState.Loading)
    val stockDataState: StateFlow<UiState<StockDetailData>> = _stockDataState.asStateFlow()

    private val _alertsState = MutableStateFlow<UiState<List<WatchItem>>>(UiState.Loading)
    val alertsState: StateFlow<UiState<List<WatchItem>>> = _alertsState.asStateFlow()

    init {
        loadStockData()
        loadAlerts()
    }

    /**
     * Hämtar aktiedata: pris, 52w high, dagsförändring, drawdown.
     */
    fun loadStockData() {
        viewModelScope.launch {
            try {
                _stockDataState.value = UiState.Loading
                
                val lastPrice = yahooFinanceService.getStockPrice(symbol)
                val previousClose = yahooFinanceService.getPreviousClose(symbol)
                val week52High = yahooFinanceService.getATH(symbol) // getATH hämtar 52w high
                val dailyChangePercent = yahooFinanceService.getDailyChangePercent(symbol)
                val companyName = yahooFinanceService.getCompanyName(symbol) ?: symbol
                
                val drawdownPercent = if (lastPrice != null && week52High != null && week52High > 0) {
                    ((week52High - lastPrice) / week52High) * 100
                } else {
                    null
                }
                
                val stockData = StockDetailData(
                    symbol = symbol,
                    companyName = companyName,
                    lastPrice = lastPrice,
                    previousClose = previousClose,
                    week52High = week52High,
                    dailyChangePercent = dailyChangePercent,
                    drawdownPercent = drawdownPercent
                )
                
                _stockDataState.value = UiState.Success(stockData)
                Log.d(TAG, "Loaded stock data for $symbol")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stock data: ${e.message}", e)
                _stockDataState.value = UiState.Error("Kunde inte ladda aktiedata: ${e.message}")
            }
        }
    }

    /**
     * Hämtar alla alerts för denna aktie.
     */
    fun loadAlerts() {
        viewModelScope.launch {
            try {
                _alertsState.value = UiState.Loading
                val allAlerts = watchItemDao.getAllWatchItems()
                
                // Filtrera alerts för denna aktie
                val stockAlerts = allAlerts.filter { watchItem ->
                    watchItem.ticker == symbol || watchItem.ticker1 == symbol || watchItem.ticker2 == symbol
                }
                
                _alertsState.value = UiState.Success(stockAlerts)
                Log.d(TAG, "Loaded ${stockAlerts.size} alerts for $symbol")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading alerts: ${e.message}", e)
                _alertsState.value = UiState.Error("Kunde inte ladda alerts: ${e.message}")
            }
        }
    }

    /**
     * Skapar en ny alert för aktien.
     */
    fun createAlert(watchType: WatchType, companyName: String) {
        viewModelScope.launch {
            try {
                val watchItem = WatchItem(
                    watchType = watchType,
                    ticker = symbol,
                    companyName = companyName,
                    isActive = true,
                    isTriggered = false,
                    lastTriggeredDate = null
                )
                
                watchItemDao.insertWatchItem(watchItem)
                loadAlerts() // Reload alerts
                Log.d(TAG, "Created alert for $symbol")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating alert: ${e.message}", e)
            }
        }
    }

    /**
     * Tar bort en alert.
     */
    fun deleteAlert(watchItem: WatchItem) {
        viewModelScope.launch {
            try {
                watchItemDao.deleteWatchItem(watchItem)
                loadAlerts() // Reload alerts
                Log.d(TAG, "Deleted alert ${watchItem.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting alert: ${e.message}", e)
            }
        }
    }

    /**
     * Återaktiverar en alert (tar bort triggad-status).
     */
    fun reactivateAlert(watchItem: WatchItem) {
        viewModelScope.launch {
            try {
                val updated = watchItem.reactivate()
                watchItemDao.update(updated)
                loadAlerts() // Reload alerts
                Log.d(TAG, "Reactivated alert ${watchItem.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error reactivating alert: ${e.message}", e)
            }
        }
    }

    /**
     * Aktiverar/inaktiverar en alert.
     */
    fun toggleAlert(watchItem: WatchItem) {
        viewModelScope.launch {
            try {
                val updated = watchItem.setActive(!watchItem.isActive)
                watchItemDao.update(updated)
                loadAlerts() // Reload alerts
                Log.d(TAG, "Toggled alert ${watchItem.id} to ${updated.isActive}")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling alert: ${e.message}", e)
            }
        }
    }

    /**
     * Uppdaterar aktiedata (refresh).
     */
    fun refresh() {
        loadStockData()
        loadAlerts()
    }
}

/**
 * Data class för aktiedetaljdata.
 */
data class StockDetailData(
    val symbol: String,
    val companyName: String,
    val lastPrice: Double?,
    val previousClose: Double?,
    val week52High: Double?,
    val dailyChangePercent: Double?,
    val drawdownPercent: Double?
)

