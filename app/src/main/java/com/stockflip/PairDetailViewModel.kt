package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.repository.TriggerHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockSummary(
    val symbol: String,
    val companyName: String?,
    val lastPrice: Double?,
    val dailyChangePercent: Double?,
    val currency: String?
)

data class PairDetailData(
    val watchItem: WatchItem,
    val stockA: StockSummary,
    val stockB: StockSummary,
    val spread: Double?
)

class PairDetailViewModel(
    private val watchItemDao: WatchItemDao,
    private val marketDataService: MarketDataService,
    private val watchItemId: Int,
    private val triggerHistoryRepository: TriggerHistoryRepository
) : ViewModel() {

    private val _pairState = MutableStateFlow<UiState<PairDetailData>>(UiState.Loading)
    val pairState: StateFlow<UiState<PairDetailData>> = _pairState.asStateFlow()

    private val _chartState = MutableStateFlow<UiState<IntradayChartData>>(UiState.Loading)
    val chartState: StateFlow<UiState<IntradayChartData>> = _chartState.asStateFlow()
    private var chartJob: Job? = null

    private val _selectedPeriod = MutableStateFlow(ChartPeriod.DAY)
    val selectedPeriod: StateFlow<ChartPeriod> = _selectedPeriod.asStateFlow()

    private val _historyState = MutableStateFlow<List<Long>>(emptyList())
    val historyState: StateFlow<List<Long>> = _historyState.asStateFlow()

    init {
        refresh()
    }

    fun selectPeriod(period: ChartPeriod) {
        _selectedPeriod.value = period
        loadChart()
    }

    fun refresh() {
        loadPair()
        loadChart()
        loadHistory()
    }

    fun toggleActive() {
        viewModelScope.launch {
            val current = (_pairState.value as? UiState.Success)?.data?.watchItem ?: return@launch
            val updated = current.setActive(!current.isActive)
            watchItemDao.update(updated)
            loadPair()
        }
    }

    fun deletePair() {
        viewModelScope.launch {
            val current = (_pairState.value as? UiState.Success)?.data?.watchItem ?: return@launch
            watchItemDao.deleteWatchItem(current)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                _historyState.value = triggerHistoryRepository.getLatest(watchItemId)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading trigger history: ${e.message}", e)
            }
        }
    }

    private fun loadPair() {
        viewModelScope.launch {
            try {
                _pairState.value = UiState.Loading
                val item = watchItemDao.getWatchItemById(watchItemId)
                if (item == null || item.watchType !is WatchType.PricePair) {
                    _pairState.value = UiState.Error("Ogiltigt aktiepar")
                    return@launch
                }
                val symbolA = item.ticker1 ?: return@launch
                val symbolB = item.ticker2 ?: return@launch

                val priceA = marketDataService.getStockPrice(symbolA)
                val priceB = marketDataService.getStockPrice(symbolB)
                val changeA = marketDataService.getDailyChangePercent(symbolA)
                val changeB = marketDataService.getDailyChangePercent(symbolB)
                val currencyA = marketDataService.getCurrency(symbolA)
                val currencyB = marketDataService.getCurrency(symbolB)

                val stockA = StockSummary(
                    symbol = symbolA,
                    companyName = item.companyName1,
                    lastPrice = priceA,
                    dailyChangePercent = changeA,
                    currency = currencyA
                )
                val stockB = StockSummary(
                    symbol = symbolB,
                    companyName = item.companyName2,
                    lastPrice = priceB,
                    dailyChangePercent = changeB,
                    currency = currencyB
                )
                val spread = if (priceA != null && priceB != null) priceA - priceB else null

                _pairState.value = UiState.Success(
                    PairDetailData(
                        watchItem = item,
                        stockA = stockA,
                        stockB = stockB,
                        spread = spread
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pair: ${e.message}", e)
                _pairState.value = UiState.Error("Kunde inte ladda aktiepar")
            }
        }
    }

    private fun loadChart() {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _chartState.value = UiState.Loading
            val item = watchItemDao.getWatchItemById(watchItemId)
            if (item == null || item.watchType !is WatchType.PricePair) {
                _chartState.value = UiState.Error("Ingen data")
                return@launch
            }
            val symbolA = item.ticker1 ?: return@launch
            val symbolB = item.ticker2 ?: return@launch

            val period = _selectedPeriod.value
            val chartA = marketDataService.getIntradayChart(symbolA, period)
            val chartB = marketDataService.getIntradayChart(symbolB, period)

            val spread = buildSpreadChart(symbolA, symbolB, chartA, chartB)
            _chartState.value = if (spread != null) UiState.Success(spread) else UiState.Error("Ingen data")
        }
    }

    private fun buildSpreadChart(
        symbolA: String,
        symbolB: String,
        chartA: IntradayChartData?,
        chartB: IntradayChartData?
    ): IntradayChartData? {
        if (chartA == null || chartB == null) return null

        val mapA = chartA.timestamps.zip(chartA.prices).toMap()
        val mapB = chartB.timestamps.zip(chartB.prices).toMap()
        val commonTimestamps = chartA.timestamps.filter { mapB.containsKey(it) }

        if (commonTimestamps.size >= 2) {
            val prices = commonTimestamps.map { ts -> (mapA[ts] ?: 0.0) - (mapB[ts] ?: 0.0) }
            val prevClose = if (chartA.previousClose != null && chartB.previousClose != null) {
                chartA.previousClose - chartB.previousClose
            } else null
            return IntradayChartData(
                timestamps = commonTimestamps,
                prices = prices,
                previousClose = prevClose,
                lastTradeTimestamp = maxOf(chartA.lastTradeTimestamp ?: 0L, chartB.lastTradeTimestamp ?: 0L),
                emptyReason = null
            )
        }

        // Fallback: använd senaste kända priser för att rita en platt spread-linje
        val fallbackA = chartA.prices.lastOrNull() ?: chartA.previousClose
        val fallbackB = chartB.prices.lastOrNull() ?: chartB.previousClose
        if (fallbackA != null && fallbackB != null) {
            val endTs = maxOf(chartA.lastTradeTimestamp ?: 0L, chartB.lastTradeTimestamp ?: 0L)
                .takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
            val startTs = (endTs - 3600L).coerceAtLeast(0L)
            val spread = fallbackA - fallbackB
            return IntradayChartData(
                timestamps = listOf(startTs, endTs),
                prices = listOf(spread, spread),
                previousClose = null,
                lastTradeTimestamp = endTs,
                emptyReason = "Reservdata"
            )
        }

        Log.w(TAG, "No common chart data for $symbolA/$symbolB")
        return IntradayChartData(
            timestamps = emptyList(),
            prices = emptyList(),
            previousClose = null,
            lastTradeTimestamp = null,
            emptyReason = "Ingen gemensam intradagsdata"
        )
    }

    companion object {
        private const val TAG = "PairDetailViewModel"
    }
}
