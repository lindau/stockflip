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
import kotlin.math.abs

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

    private val _chartState = MutableStateFlow<UiState<PairChartData>>(UiState.Loading)
    val chartState: StateFlow<UiState<PairChartData>> = _chartState.asStateFlow()
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

    fun reactivate() {
        viewModelScope.launch {
            val current = (_pairState.value as? UiState.Success)?.data?.watchItem ?: return@launch
            watchItemDao.update(current.reactivate().copy(isActive = true))
            loadPair()
            loadHistory()
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

            val pairChart = buildPairChart(item, chartA, chartB)
            _chartState.value = if (pairChart != null) UiState.Success(pairChart) else UiState.Error("Ingen data")
        }
    }

    private fun buildPairChart(
        item: WatchItem,
        chartA: IntradayChartData?,
        chartB: IntradayChartData?
    ): PairChartData? {
        if (chartA == null || chartB == null) return null

        val pairType = item.watchType as? WatchType.PricePair ?: return null
        val stockALabel = item.companyName1 ?: item.ticker1 ?: "Aktie A"
        val stockBLabel = item.companyName2 ?: item.ticker2 ?: "Aktie B"
        val lastTradeTimestamp = maxOf(chartA.lastTradeTimestamp ?: 0L, chartB.lastTradeTimestamp ?: 0L)
            .takeIf { it > 0L }

        val mapA = chartA.timestamps.zip(chartA.prices).toMap()
        val mapB = chartB.timestamps.zip(chartB.prices).toMap()
        val commonTimestamps = chartA.timestamps.filter { mapB.containsKey(it) }

        if (commonTimestamps.size >= 2) {
            val pricesA = commonTimestamps.mapNotNull(mapA::get)
            val pricesB = commonTimestamps.mapNotNull(mapB::get)
            if (pricesA.size == commonTimestamps.size && pricesB.size == commonTimestamps.size) {
                val normalizedA = normalizeSeries(commonTimestamps, pricesA)
                val normalizedB = normalizeSeries(commonTimestamps, pricesB)
                if (normalizedA.values.isNotEmpty() && normalizedB.values.isNotEmpty()) {
                    val spreadPrices = pricesA.zip(pricesB) { a, b -> a - b }
                    val currentSpread = spreadPrices.lastOrNull()
                    return PairChartData(
                        stockALabel = stockALabel,
                        stockBLabel = stockBLabel,
                        normalizedA = normalizedA,
                        normalizedB = normalizedB,
                        spread = IntradayChartData(
                            timestamps = commonTimestamps,
                            prices = spreadPrices,
                            previousClose = if (chartA.previousClose != null && chartB.previousClose != null) {
                                chartA.previousClose - chartB.previousClose
                            } else null,
                            lastTradeTimestamp = lastTradeTimestamp,
                            emptyReason = null
                        ),
                        spreadTarget = pairType.priceDifference.takeIf { it > 0.0 },
                        showEqualLine = pairType.notifyWhenEqual,
                        currentSpread = currentSpread,
                        distanceToTarget = distanceToTarget(pairType.priceDifference, currentSpread),
                        leaderLabel = determineLeader(
                            stockALabel,
                            stockBLabel,
                            normalizedA.values.last(),
                            normalizedB.values.last()
                        ),
                        lastTradeTimestamp = lastTradeTimestamp,
                        emptyReason = null
                    )
                }
            }
        }

        return buildFallbackPairChart(
            chartA = chartA,
            chartB = chartB,
            pairType = pairType,
            stockALabel = stockALabel,
            stockBLabel = stockBLabel,
            lastTradeTimestamp = lastTradeTimestamp
        ) ?: PairChartData(
            stockALabel = stockALabel,
            stockBLabel = stockBLabel,
            normalizedA = PairChartSeries(emptyList(), emptyList()),
            normalizedB = PairChartSeries(emptyList(), emptyList()),
            spread = IntradayChartData(
                timestamps = emptyList(),
                prices = emptyList(),
                previousClose = null,
                lastTradeTimestamp = lastTradeTimestamp,
                emptyReason = "Ingen gemensam intradagsdata"
            ),
            spreadTarget = pairType.priceDifference.takeIf { it > 0.0 },
            showEqualLine = pairType.notifyWhenEqual,
            currentSpread = null,
            distanceToTarget = null,
            leaderLabel = null,
            lastTradeTimestamp = lastTradeTimestamp,
            emptyReason = "Ingen gemensam intradagsdata"
        )
    }

    private fun normalizeSeries(timestamps: List<Long>, prices: List<Double>): PairChartSeries {
        val basePrice = prices.firstOrNull()?.takeIf { it > 0.0 } ?: return PairChartSeries(emptyList(), emptyList())
        return PairChartSeries(
            timestamps = timestamps,
            values = prices.map { price -> (price / basePrice) * 100.0 }
        )
    }

    private fun determineLeader(stockALabel: String, stockBLabel: String, valueA: Double, valueB: Double): String {
        val delta = valueA - valueB
        return when {
            abs(delta) < 0.05 -> "Jämnt lopp"
            delta > 0 -> stockALabel
            else -> stockBLabel
        }
    }

    private fun buildFallbackPairChart(
        chartA: IntradayChartData,
        chartB: IntradayChartData,
        pairType: WatchType.PricePair,
        stockALabel: String,
        stockBLabel: String,
        lastTradeTimestamp: Long?
    ): PairChartData? {
        val endTs = lastTradeTimestamp ?: (System.currentTimeMillis() / 1000L)
        val startTs = (endTs - 3600L).coerceAtLeast(0L)
        val timestamps = listOf(startTs, endTs)

        val baseA = chartA.previousClose ?: chartA.prices.firstOrNull() ?: chartA.prices.lastOrNull()
        val lastA = chartA.prices.lastOrNull() ?: baseA
        val baseB = chartB.previousClose ?: chartB.prices.firstOrNull() ?: chartB.prices.lastOrNull()
        val lastB = chartB.prices.lastOrNull() ?: baseB

        if (baseA == null || lastA == null || baseB == null || lastB == null || baseA <= 0.0 || baseB <= 0.0) {
            Log.w(TAG, "No fallback chart data available for pair detail")
            return null
        }

        val normalizedA = PairChartSeries(
            timestamps = timestamps,
            values = listOf(100.0, (lastA / baseA) * 100.0)
        )
        val normalizedB = PairChartSeries(
            timestamps = timestamps,
            values = listOf(100.0, (lastB / baseB) * 100.0)
        )
        val previousSpread = if (chartA.previousClose != null && chartB.previousClose != null) {
            chartA.previousClose - chartB.previousClose
        } else null
        val currentSpread = lastA - lastB

        return PairChartData(
            stockALabel = stockALabel,
            stockBLabel = stockBLabel,
            normalizedA = normalizedA,
            normalizedB = normalizedB,
            spread = IntradayChartData(
                timestamps = timestamps,
                prices = listOf(previousSpread ?: currentSpread, currentSpread),
                previousClose = previousSpread,
                lastTradeTimestamp = endTs,
                emptyReason = "Reservdata"
            ),
            spreadTarget = pairType.priceDifference.takeIf { it > 0.0 },
            showEqualLine = pairType.notifyWhenEqual,
            currentSpread = currentSpread,
            distanceToTarget = distanceToTarget(pairType.priceDifference, currentSpread),
            leaderLabel = determineLeader(stockALabel, stockBLabel, normalizedA.values.last(), normalizedB.values.last()),
            lastTradeTimestamp = endTs,
            emptyReason = "Reservdata"
        )
    }

    private fun distanceToTarget(target: Double, currentSpread: Double?): Double? {
        if (target <= 0.0 || currentSpread == null) return null
        return (target - abs(currentSpread)).coerceAtLeast(0.0)
    }

    companion object {
        private const val TAG = "PairDetailViewModel"
    }
}
