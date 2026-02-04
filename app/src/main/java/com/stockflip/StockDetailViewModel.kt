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
    private val yahooFinanceService: MarketDataService,
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
                val week52Low = yahooFinanceService.get52WeekLow(symbol)
                val currency = yahooFinanceService.getCurrency(symbol) ?: "SEK"
                val exchange = yahooFinanceService.getExchange(symbol)
                var dailyChangePercent = yahooFinanceService.getDailyChangePercent(symbol)
                
                // Fallback-beräkning om API returnerar null men vi har både pris och previousClose
                if (dailyChangePercent == null && lastPrice != null && previousClose != null && previousClose > 0) {
                    dailyChangePercent = ((lastPrice - previousClose) / previousClose) * 100
                    Log.d(TAG, "Calculated daily change as fallback: $dailyChangePercent%")
                }
                
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
                    week52Low = week52Low,
                    currency = currency,
                    exchange = exchange,
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
     * Hämtar alla alerts för denna aktie och uppdaterar med aktuella priser.
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
                
                // Uppdatera med aktuella priser
                val updatedAlerts = stockAlerts.map { item ->
                    when (item.watchType) {
                        is WatchType.PricePair -> {
                            if (item.ticker1 != null && item.ticker2 != null) {
                                val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                                val price2 = yahooFinanceService.getStockPrice(item.ticker2)
                                if (price1 != null && price2 != null) {
                                    item.withCurrentPrices(price1, price2)
                                } else {
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.PriceTarget,
                        is WatchType.PriceRange,
                        is WatchType.DailyMove -> {
                            val ticker = item.ticker ?: symbol
                            val price = yahooFinanceService.getStockPrice(ticker)
                            if (price != null) {
                                item.withCurrentPrice(price)
                            } else {
                                item
                            }
                        }
                        is WatchType.ATHBased -> {
                            val ticker = item.ticker ?: symbol
                            val ath = yahooFinanceService.getATH(ticker)
                            val price = yahooFinanceService.getStockPrice(ticker)
                            if (ath != null && price != null) {
                                item.withATHData(ath, price)
                            } else if (price != null) {
                                item.withCurrentPrice(price)
                            } else {
                                item
                            }
                        }
                        is WatchType.KeyMetrics -> {
                            val ticker = item.ticker ?: symbol
                            val metricType = (item.watchType as? WatchType.KeyMetrics)?.metricType
                            if (metricType != null) {
                                val metricValue = yahooFinanceService.getKeyMetric(ticker, metricType)
                                if (metricValue != null) {
                                    item.withCurrentMetricValue(metricValue)
                                } else {
                                    item
                                }
                            } else {
                                item
                            }
                        }
                        is WatchType.Combined -> {
                            // Combined alerts behöver inte uppdateras här
                            item
                        }
                    }
                }
                
                _alertsState.value = UiState.Success(updatedAlerts)
                Log.d(TAG, "Loaded ${updatedAlerts.size} alerts for $symbol")
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
     * Uppdaterar en befintlig alert.
     */
    fun updateWatchItem(watchItem: WatchItem) {
        viewModelScope.launch {
            try {
                watchItemDao.update(watchItem)
                loadAlerts() // Reload alerts
                Log.d(TAG, "Updated alert ${watchItem.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating alert: ${e.message}", e)
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
    val week52Low: Double?,
    val currency: String = "SEK",
    val exchange: String? = null,
    val dailyChangePercent: Double?,
    val drawdownPercent: Double?
)

