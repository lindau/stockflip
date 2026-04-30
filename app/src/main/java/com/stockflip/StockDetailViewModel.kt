package com.stockflip

import android.util.Log
import com.stockflip.CurrencyHelper
import com.stockflip.repository.MetricHistoryRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.repository.TriggerHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel för aktiedetaljvy.
 * Hanterar datahämtning och alert-hantering för en enskild aktie.
 */
class StockDetailViewModel(
    private val watchItemDao: WatchItemDao,
    private val yahooFinanceService: MarketDataService,
    private val symbol: String,
    private val triggerHistoryRepository: TriggerHistoryRepository,
    private val stockNoteDao: StockNoteDao,
    private val metricHistoryRepository: MetricHistoryRepository
) : ViewModel() {

    private val TAG = "StockDetailViewModel"

    private val _stockDataState = MutableStateFlow<UiState<StockDetailData>>(UiState.Loading)
    val stockDataState: StateFlow<UiState<StockDetailData>> = _stockDataState.asStateFlow()

    private val _alertsState = MutableStateFlow<UiState<List<WatchItemUiState>>>(UiState.Loading)
    val alertsState: StateFlow<UiState<List<WatchItemUiState>>> = _alertsState.asStateFlow()

    private val _chartState = MutableStateFlow<UiState<IntradayChartData>>(UiState.Loading)
    val chartState: StateFlow<UiState<IntradayChartData>> = _chartState.asStateFlow()
    private var chartLoadingJob: Job? = null

    private val _selectedPeriod = MutableStateFlow(ChartPeriod.DAY)
    val selectedPeriod: StateFlow<ChartPeriod> = _selectedPeriod.asStateFlow()

    private val _triggerHistoryState = MutableStateFlow<Map<Int, List<Long>>>(emptyMap())
    val triggerHistoryState: StateFlow<Map<Int, List<Long>>> = _triggerHistoryState.asStateFlow()

    private val _metricHistoryState = MutableStateFlow<Map<WatchType.MetricType, MetricHistorySummary>>(emptyMap())
    val metricHistoryState: StateFlow<Map<WatchType.MetricType, MetricHistorySummary>> = _metricHistoryState.asStateFlow()

    val noteState: StateFlow<StockNote?> = stockNoteDao.getByTickerFlow(symbol)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun saveNote(text: String) {
        viewModelScope.launch {
            if (text.isBlank()) stockNoteDao.deleteByTicker(symbol)
            else stockNoteDao.upsert(StockNote(symbol, text.trim()))
        }
    }

    init {
        loadStockData()
        loadChartData()
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
                    drawdownPercent = drawdownPercent,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                _stockDataState.value = UiState.Success(stockData)
                Log.d(TAG, "Loaded stock data for $symbol")

                launch {
                    try {
                        val allTimeHigh = yahooFinanceService.getAllTimeHigh(symbol)
                        if (allTimeHigh != null && lastPrice != null && allTimeHigh > 0.0) {
                            val effectiveHigh = if (lastPrice > allTimeHigh) lastPrice else allTimeHigh
                            val allTimeDrawdownPercent = ((effectiveHigh - lastPrice) / effectiveHigh) * 100
                            val currentData = (_stockDataState.value as? UiState.Success<StockDetailData>)?.data ?: stockData
                            _stockDataState.value = UiState.Success(
                                currentData.copy(
                                    allTimeHigh = effectiveHigh,
                                    allTimeDrawdownPercent = allTimeDrawdownPercent
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading all-time high for $symbol: ${e.message}", e)
                    }
                }

                if (!StockSearchResult.isCryptoSymbol(symbol)) {
                    launch { loadMetricHistory() }
                } else {
                    _metricHistoryState.value = emptyMap()
                }

                // Hämta nyckeltal asynkront och uppdatera state när de anländer
                if (!StockSearchResult.isCryptoSymbol(symbol)) {
                    launch {
                        try {
                            val metrics = yahooFinanceService.getAllKeyMetrics(symbol)
                            if (metrics != null) {
                                val currentData = (_stockDataState.value as? UiState.Success<StockDetailData>)?.data ?: stockData
                                _stockDataState.value = UiState.Success(currentData.copy(
                                    peRatio = metrics.peRatio,
                                    psRatio = metrics.psRatio,
                                    dividendYield = metrics.dividendYield
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading key metrics for $symbol: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stock data: ${e.message}", e)
                _stockDataState.value = UiState.Error("Kunde inte ladda aktiedata: ${e.message}")
            }
        }
    }

    private suspend fun loadMetricHistory() {
        try {
            val summaries = WatchType.MetricType.entries.mapNotNull { metricType ->
                metricHistoryRepository.getMetricHistorySummary(symbol, metricType)?.let { metricType to it }
            }.toMap()
            _metricHistoryState.value = summaries
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metric history for $symbol: ${e.message}", e)
            _metricHistoryState.value = emptyMap()
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
                    val inactiveItems = items.filter { !it.isActive }.map { WatchItemUiState(it) }
                    val updatedAlerts = sortAlertsForStockPage(activeItems + inactiveItems)
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

    private fun sortAlertsForStockPage(alerts: List<WatchItemUiState>): List<WatchItemUiState> {
        return alerts.sortedWith(
            compareBy<WatchItemUiState> { stockPageWatchTypeRank(it.item.watchType) }
                .thenBy { stockPageScaleGroupRank(it.item.watchType) }
                .thenBy { stockPageScaleStart(it.item.watchType) }
                .thenBy { stockPageScaleEnd(it.item.watchType) }
                .thenBy { if (it.item.isActive) 0 else 1 }
                .thenBy { if (it.item.isTriggered) 1 else 0 }
                .thenBy { it.item.getDisplayName().lowercase() }
                .thenBy { it.item.id }
        )
    }

    private fun stockPageWatchTypeRank(watchType: WatchType): Int {
        return when (watchType) {
            is WatchType.PriceTarget -> 0
            is WatchType.PriceRange -> 1
            is WatchType.ATHBased -> 2
            is WatchType.KeyMetrics -> 3
            is WatchType.DailyMove -> 4
            is WatchType.Combined -> 5
            is WatchType.PricePair -> 6
        }
    }

    private fun stockPageScaleGroupRank(watchType: WatchType): Int {
        return when (watchType) {
            is WatchType.ATHBased -> watchType.reference.ordinal * 10 + watchType.dropType.ordinal
            is WatchType.KeyMetrics -> watchType.metricType.ordinal * 10 + watchType.direction.ordinal
            is WatchType.DailyMove -> watchType.direction.ordinal
            else -> 0
        }
    }

    private fun stockPageScaleStart(watchType: WatchType): Double {
        return when (watchType) {
            is WatchType.PriceTarget -> watchType.targetPrice
            is WatchType.PriceRange -> watchType.minPrice
            is WatchType.ATHBased -> watchType.dropValue
            is WatchType.KeyMetrics -> watchType.targetValue
            is WatchType.DailyMove -> watchType.percentThreshold
            is WatchType.PricePair -> watchType.priceDifference
            is WatchType.Combined -> Double.MAX_VALUE
        }
    }

    private fun stockPageScaleEnd(watchType: WatchType): Double {
        return when (watchType) {
            is WatchType.PriceRange -> watchType.maxPrice
            else -> Double.MAX_VALUE
        }
    }

    /**
     * Hämtar aktuella priser för en lista bevakningar.
     */
    private suspend fun fetchPricesForItems(items: List<WatchItem>): List<WatchItemUiState> {
        val now = System.currentTimeMillis()
        return items.map { item ->
            when (item.watchType) {
                is WatchType.PricePair -> {
                    if (item.ticker1 != null && item.ticker2 != null) {
                        val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                        val price2 = yahooFinanceService.getStockPrice(item.ticker2)
                        if (price1 != null && price2 != null)
                            WatchItemUiState(item, LiveWatchData(currentPrice1 = price1, currentPrice2 = price2, lastUpdatedAt = now))
                        else WatchItemUiState(item, LiveWatchData(updateFailed = true))
                    } else WatchItemUiState(item)
                }
                is WatchType.PriceTarget,
                is WatchType.PriceRange,
                is WatchType.DailyMove -> {
                    val ticker = item.ticker ?: symbol
                    val price = yahooFinanceService.getStockPrice(ticker)
                    val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                    if (price != null)
                        WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                    else WatchItemUiState(item, LiveWatchData(updateFailed = true))
                }
                is WatchType.ATHBased -> {
                    val ticker = item.ticker ?: symbol
                    val high = when (item.watchType.reference) {
                        WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> yahooFinanceService.getATH(ticker)
                        WatchType.HighReference.ALL_TIME_HIGH -> yahooFinanceService.getAllTimeHigh(ticker)
                    }
                    val price = yahooFinanceService.getStockPrice(ticker)
                    val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                    when {
                        high != null && price != null && high > 0.0 -> {
                            val effectiveHigh = if (price > high) price else high
                            WatchItemUiState(
                                item,
                                LiveWatchData(
                                    currentATH = effectiveHigh,
                                    currentPrice = price,
                                    currentDropPercentage = ((effectiveHigh - price) / effectiveHigh) * 100,
                                    currentDropAbsolute = effectiveHigh - price,
                                    currentDailyChangePercent = changePercent,
                                    lastUpdatedAt = now
                                )
                            )
                        }
                        price != null -> WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        else -> WatchItemUiState(item, LiveWatchData(updateFailed = true))
                    }
                }
                is WatchType.KeyMetrics -> {
                    val ticker = item.ticker ?: symbol
                    val metricType = item.watchType.metricType
                    val metricValue = yahooFinanceService.getKeyMetric(ticker, metricType)
                    val price = yahooFinanceService.getStockPrice(ticker)
                    val changePercent = yahooFinanceService.getDailyChangePercent(ticker)
                    when {
                        metricValue != null -> WatchItemUiState(item, LiveWatchData(
                            currentMetricValue = metricValue, metricValueAtCreation = metricValue,
                            currentPrice = price ?: 0.0, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        price != null -> WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        else -> WatchItemUiState(item, LiveWatchData(updateFailed = true))
                    }
                }
                is WatchType.Combined -> WatchItemUiState(item)
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
                val updatedAlerts = sortAlertsForStockPage(fetchPricesForItems(items))
                _alertsState.value = UiState.Success(updatedAlerts)
                Log.d(TAG, "Manual refresh: ${updatedAlerts.size} alerts for $symbol")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading alerts: ${e.message}", e)
                _alertsState.value = UiState.Error("Kunde inte ladda alerts: ${e.message}")
            }
        }
    }

    /**
     * Returnerar true om en aktiv bevakning med exakt samma inställningar redan finns.
     * Combined-bevakningar kontrolleras aldrig.
     */
    suspend fun isDuplicateWatch(watchType: WatchType): Boolean {
        if (watchType is WatchType.Combined) return false
        return watchItemDao.getWatchItemsBySymbol(symbol)
            .any { it.isActive && it.watchType == watchType }
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

    fun selectPeriod(period: ChartPeriod) {
        _selectedPeriod.value = period
        loadChartData()
    }

    fun loadChartData() {
        chartLoadingJob?.cancel()
        chartLoadingJob = viewModelScope.launch {
            _chartState.value = UiState.Loading
            // Försök hämta grafdata — retry efter 2 sekunder vid fel (t.ex. race condition med cookie-session)
            var data = fetchChartData()
            if (data == null) {
                delay(2_000L)
                data = fetchChartData()
            }
            _chartState.value = if (data != null) {
                UiState.Success(data)
            } else {
                UiState.Success(
                    IntradayChartData(
                        timestamps = emptyList(),
                        prices = emptyList(),
                        previousClose = null,
                        lastTradeTimestamp = null,
                        emptyReason = "Ingen intradagsdata tillgänglig"
                    )
                )
            }
        }
    }

    private suspend fun fetchChartData(): IntradayChartData? = try {
        yahooFinanceService.getIntradayChart(symbol, _selectedPeriod.value)
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching chart for $symbol: ${e.message}", e)
        null
    }

    /**
     * Uppdaterar aktiedata (refresh).
     */
    fun refresh() {
        loadStockData()
        loadChartData()
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
    val drawdownPercent: Double?,
    val allTimeHigh: Double? = null,
    val allTimeDrawdownPercent: Double? = null,
    val peRatio: Double? = null,
    val psRatio: Double? = null,
    val dividendYield: Double? = null,
    val lastUpdatedAt: Long = 0L
)
