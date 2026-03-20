package com.stockflip

import android.util.Log
import com.stockflip.CurrencyHelper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.repository.TriggerHistoryRepository
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
    private val symbol: String,
    private val triggerHistoryRepository: TriggerHistoryRepository
) : ViewModel() {

    private val TAG = "StockDetailViewModel"

    private val _stockDataState = MutableStateFlow<UiState<StockDetailData>>(UiState.Loading)
    val stockDataState: StateFlow<UiState<StockDetailData>> = _stockDataState.asStateFlow()

    private val _alertsState = MutableStateFlow<UiState<List<WatchItem>>>(UiState.Loading)
    val alertsState: StateFlow<UiState<List<WatchItem>>> = _alertsState.asStateFlow()

    private val _triggerHistoryState = MutableStateFlow<Map<Int, List<Long>>>(emptyMap())
    val triggerHistoryState: StateFlow<Map<Int, List<Long>>> = _triggerHistoryState.asStateFlow()

    init {
        loadStockData()
        observeAlerts()
    }

    /**
     * Hämtar aktiedata från ett enda chart-anrop: pris, 52w high/low, dagsförändring, drawdown.
     * Dagsförändring beräknas endast när både lastPrice och previousClose finns i samma svar.
     */
    fun loadStockData() {
        viewModelScope.launch {
            try {
                _stockDataState.value = UiState.Loading
                val snapshot: StockDetailSnapshot? = yahooFinanceService.getStockDetailSnapshot(symbol)
                if (snapshot == null) {
                    _stockDataState.value = UiState.Error("Kunde inte ladda aktiedata för $symbol")
                    Log.e(TAG, "getStockDetailSnapshot returned null for $symbol")
                    return@launch
                }
                val lastPrice: Double? = snapshot.lastPrice
                val previousClose: Double? = snapshot.previousClose
                val week52High: Double? = snapshot.week52High
                val week52Low: Double? = snapshot.week52Low
                val currency: String = snapshot.currency ?: CurrencyHelper.getCurrencyFromSymbol(symbol)
                val exchange: String? = snapshot.exchangeName
                val companyName: String = snapshot.companyName ?: symbol
                val dailyChangePercent: Double? = snapshot.dailyChangePercent
                    ?: if (lastPrice != null && previousClose != null && previousClose > 0) {
                        ((lastPrice - previousClose) / previousClose) * 100
                    } else null
                val drawdownPercent: Double? = if (lastPrice != null && week52High != null && week52High > 0) {
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
     * Observerar bevakningar för denna aktie reaktivt via Room Flow.
     * Uppdateras automatiskt när databasen ändras (insert/delete/update).
     */
    private fun observeAlerts() {
        viewModelScope.launch {
            watchItemDao.getWatchItemsBySymbolFlow(symbol).collect { items ->
                try {
                    val activeItems = fetchPricesForItems(items.filter { it.isActive })
                    val inactiveItems = items.filter { !it.isActive }
                    val updatedAlerts = activeItems + inactiveItems
                    _alertsState.value = UiState.Success(updatedAlerts)
                    val history = items.associate { item ->
                        item.id to triggerHistoryRepository.getLatest(item.id)
                    }
                    _triggerHistoryState.value = history
                    Log.d(TAG, "Reactive update: ${updatedAlerts.size} alerts for $symbol")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating alerts: ${e.message}", e)
                    _alertsState.value = UiState.Error("Kunde inte ladda alerts: ${e.message}")
                }
            }
        }
    }

    /**
     * Hämtar aktuella priser för en lista bevakningar.
     */
    private suspend fun fetchPricesForItems(items: List<WatchItem>): List<WatchItem> {
        return items.map { item ->
            when (item.watchType) {
                is WatchType.PricePair -> {
                    if (item.ticker1 != null && item.ticker2 != null) {
                        val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                        val price2 = yahooFinanceService.getStockPrice(item.ticker2)
                        if (price1 != null && price2 != null) item.withCurrentPrices(price1, price2) else item
                    } else item
                }
                is WatchType.PriceTarget,
                is WatchType.PriceRange,
                is WatchType.DailyMove -> {
                    val ticker = item.ticker ?: symbol
                    val price = yahooFinanceService.getStockPrice(ticker)
                    val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                    if (price != null) item.withCurrentPriceAndDailyChange(price, changePercent) else item
                }
                is WatchType.ATHBased -> {
                    val ticker = item.ticker ?: symbol
                    val ath = yahooFinanceService.getATH(ticker)
                    val price = yahooFinanceService.getStockPrice(ticker)
                    val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                    when {
                        ath != null && price != null -> item.withATHData(ath, price).withDailyChangePercent(changePercent)
                        price != null -> item.withCurrentPriceAndDailyChange(price, changePercent)
                        else -> item
                    }
                }
                is WatchType.KeyMetrics -> {
                    val ticker = item.ticker ?: symbol
                    val metricType = (item.watchType as? WatchType.KeyMetrics)?.metricType
                    if (metricType != null) {
                        val metricValue = yahooFinanceService.getKeyMetric(ticker, metricType)
                        val price = yahooFinanceService.getStockPrice(ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                        when {
                            metricValue != null && price != null -> item.withCurrentMetricValue(metricValue).withCurrentPriceAndDailyChange(price, changePercent)
                            metricValue != null -> item.withCurrentMetricValue(metricValue)
                            price != null -> item.withCurrentPriceAndDailyChange(price, changePercent)
                            else -> item
                        }
                    } else item
                }
                is WatchType.Combined -> item
            }
        }
    }

    /**
     * Uppdaterar priser manuellt (används av refresh-knappen).
     */
    fun loadAlerts() {
        viewModelScope.launch {
            try {
                val items = watchItemDao.getAllWatchItems().filter { watchItem ->
                    watchItem.ticker == symbol || watchItem.ticker1 == symbol || watchItem.ticker2 == symbol
                }
                val updatedAlerts = fetchPricesForItems(items)
                _alertsState.value = UiState.Success(updatedAlerts)
                Log.d(TAG, "Manual refresh: ${updatedAlerts.size} alerts for $symbol")
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
                watchItemDao.update(watchItem.reactivate())
                Log.d(TAG, "Updated alert ${watchItem.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating alert: ${e.message}", e)
            }
        }
    }

    /**
     * Tar bort alla bevakningar för denna aktie.
     */
    fun deleteStock() {
        viewModelScope.launch {
            try {
                watchItemDao.deleteBySymbol(symbol)
                Log.d(TAG, "Deleted all watches for $symbol")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting stock watches: ${e.message}", e)
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

