package com.stockflip

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.backup.BackupManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class MainViewModel(
    private val stockPairDao: StockPairDao,
    private val watchItemDao: WatchItemDao,
    private val yahooFinanceService: MarketDataService,
    private val stockNoteDao: StockNoteDao,
    private val podcastObservationDao: PodcastObservationDao,
) : ViewModel() {

    private val _watchItemUiState = MutableStateFlow<UiState<List<WatchItemUiState>>>(UiState.Loading)
    val watchItemUiState: StateFlow<UiState<List<WatchItemUiState>>> = _watchItemUiState.asStateFlow()

    // True medan live-priser hämtas i bakgrunden. Driver en icke-blockerande spinner i UI:t,
    // till skillnad från UiState.Loading som ersätter hela skärmen.
    /**
     * Valt filter i Bevakningar. Fragmentet skapas om vid varje flikbyte, så filtret sparas här
     * för att användaren ska komma tillbaka till samma vy i stället för "Alla".
     */
    internal var selectedAlertsFilter: AlertsFilter = AlertsFilter.ALL

    private val _watchItemsRefreshing = MutableStateFlow(false)
    val watchItemsRefreshing: StateFlow<Boolean> = _watchItemsRefreshing.asStateFlow()

    // Engångshändelse för misslyckade användaråtgärder (lägg till/ta bort/uppdatera).
    // Hålls separat från watchItemUiState så att ett enskilt fel inte ersätter listan.
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError: SharedFlow<String> = _actionError.asSharedFlow()

    val notedTickers: StateFlow<Set<String>> = stockNoteDao.getAllTickersFlow()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val mentionedTickers: StateFlow<Set<String>> = podcastObservationDao.getAllTickersFlow()
        .map { tickers -> tickers.map { it.uppercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private var isRefreshing = false

    init {
        Log.d(TAG, "MainViewModel initialized")
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(120_000)
                try {
                    refreshWatchItems(showLoading = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-refresh failed: ${e.message}")
                }
            }
        }
    }

    suspend fun loadWatchItems() {
        loadWatchItems(forceShowStaleData = false)
    }

    suspend fun loadWatchItems(forceShowStaleData: Boolean) {
        try {
            Log.d(TAG, "Loading watch items from database")
            // Blinka inte skelett om vi redan visar data — bara vid äkta första laddning.
            if (_watchItemUiState.value !is UiState.Success) {
                _watchItemUiState.value = UiState.Loading
            }
            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Loaded ${items.size} watch items")

            // KeyMetrics currentMetricValue is @Ignore and not saved to database,
            // so we need to refresh to get the actual values.
            val hasKeyMetrics = items.any { it.watchType is WatchType.KeyMetrics }

            // Visa alltid DB-datan direkt så skärmen aldrig fastnar i Loading.
            _watchItemUiState.value = UiState.Success(items.map { WatchItemUiState(it) })
            Log.d(TAG, "Set UI state to Success with ${items.size} watch items (stale=${forceShowStaleData || hasKeyMetrics})")

            if (hasKeyMetrics && !forceShowStaleData) {
                // KeyMetrics-värden saknas i DB — hämta dem tyst i bakgrunden (spinner via refreshWatchItems).
                viewModelScope.launch { refreshWatchItems(showLoading = false) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading watch items: ${e.message}")
            _watchItemUiState.value = UiState.Error("Kunde inte läsa in bevakningarna")
        }
    }

    suspend fun refreshWatchItems(showLoading: Boolean = true) {
        // Prevent concurrent refresh calls
        if (isRefreshing) {
            Log.d(TAG, "Refresh already in progress, skipping duplicate call")
            return
        }
        
        isRefreshing = true
        _watchItemsRefreshing.value = true
        try {
            Log.d(TAG, "=== START refreshWatchItems() ===")
            // Senast kända värden per bevakning — visas (markerade som inaktuella) om hämtningen misslyckas.
            // Läses innan Loading sätts, annars går de förlorade.
            val previousLive = currentLiveById()
            if (showLoading) {
                _watchItemUiState.value = UiState.Loading
                Log.d(TAG, "Set UI state to Loading")
            }

            val items = watchItemDao.getAllWatchItems()
            Log.d(TAG, "Found ${items.size} watch items to refresh")

            // Parallellisera med max 4 samtida anrop för att undvika rate limiting.
            // Resultaten visas i omgångar allteftersom de blir klara (högst var 250:e ms), så att
            // en långsam eller hängande aktie (timeout upp till 2×15 s) inte håller tillbaka
            // nya kurser för alla andra. Ej klara bevakningar behåller senast kända värden.
            val semaphore = Semaphore(4)
            val results = arrayOfNulls<WatchItemUiState>(items.size)
            val emitLock = Mutex()
            var lastPartialEmitAt = 0L
            coroutineScope {
                items.forEachIndexed { index, item ->
                    launch {
                        val result = semaphore.withPermit { fetchLiveState(item, previousLive) }
                        emitLock.withLock {
                            results[index] = result
                            val now = System.currentTimeMillis()
                            val pendingCount = results.count { it == null }
                            if (pendingCount > 0 && now - lastPartialEmitAt >= PARTIAL_EMIT_INTERVAL_MS) {
                                lastPartialEmitAt = now
                                _watchItemUiState.value = UiState.Success(
                                    mergeWithPrevious(items, results, previousLive)
                                )
                            }
                        }
                    }
                }
            }
            val updatedItems = results.map { it!! }

            Log.d(TAG, "Refresh complete, built ${updatedItems.size} WatchItemUiState objects")

            // Update StateFlow
            try {
                Log.d(TAG, "About to update StateFlow with ${updatedItems.size} items")
                _watchItemUiState.value = UiState.Success(updatedItems)
                Log.d(TAG, "Set UI state to Success with ${updatedItems.size} watch items")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Error updating StateFlow: ${e.message}", e)
                throw e
            }
            Log.d(TAG, "=== END refreshWatchItems() - SUCCESS ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== END refreshWatchItems() - ERROR: ${e.message} ===", e)
            if (showLoading) {
                _watchItemUiState.value = UiState.Error("Kunde inte uppdatera bevakningarna")
            }
            Log.d(TAG, "Set UI state to Error")
        } finally {
            isRefreshing = false
            _watchItemsRefreshing.value = false
            Log.d(TAG, "Refresh flag reset")
        }
    }

    // Åtgärderna nedan returnerar true om de lyckades. Vid fel skickas ett meddelande via
    // actionError och false returneras, så att anroparen inte visar ett lyckat-meddelande.
    suspend fun addWatchItem(watchItem: WatchItem): Boolean {
        return try {
            Log.d(TAG, "Adding watch item")
            watchItemDao.insertWatchItem(watchItem)
            syncWatchItemsAfterMutation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding watch item: ${e.message}")
            _actionError.tryEmit("Kunde inte lägga till bevakningen")
            false
        }
    }

    suspend fun deleteStockBySymbol(symbol: String): Boolean {
        return try {
            Log.d(TAG, "Deleting all watches for symbol")
            watchItemDao.deleteBySymbol(symbol)
            syncWatchItemsAfterMutation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watches for symbol: ${e.message}")
            _actionError.tryEmit("Kunde inte ta bort bevakningarna")
            false
        }
    }

    suspend fun deleteWatchItem(watchItem: WatchItem): Boolean {
        return try {
            Log.d(TAG, "Deleting watch item")
            watchItemDao.deleteWatchItem(watchItem)
            syncWatchItemsAfterMutation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watch item: ${e.message}")
            _actionError.tryEmit("Kunde inte ta bort bevakningen")
            false
        }
    }

    suspend fun toggleWatchItemActive(watchItem: WatchItem, isActive: Boolean): Boolean {
        return try {
            Log.d(TAG, "Toggling watch item active state")
            watchItemDao.update(watchItem.setActive(isActive))
            syncWatchItemsAfterMutation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling watch item active state: ${e.message}")
            _actionError.tryEmit("Kunde inte uppdatera bevakningen")
            false
        }
    }

    suspend fun reactivateWatchItem(watchItem: WatchItem): WatchReactivationResult {
        try {
            Log.d(TAG, "Reactivating watch item")
            val keepLastTriggeredDate = shouldGuardAgainstImmediateRetrigger(watchItem)
            val updatedWatchItem: WatchItem = watchItem.reactivate(
                currentPrice = currentPriceForReactivation(watchItem),
                keepLastTriggeredDate = keepLastTriggeredDate
            )
            watchItemDao.update(updatedWatchItem)
            syncWatchItemsAfterMutation()
            return WatchReactivationResult(
                watchItem = updatedWatchItem,
                sameDayTriggerGuarded = keepLastTriggeredDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reactivating watch item: ${e.message}")
            // Anroparen visar felet själv; listtillståndet lämnas orört.
            throw e
        }
    }

    suspend fun updateWatchItem(watchItem: WatchItem): Boolean {
        return try {
            Log.d(TAG, "Updating watch item")
            val keepLastTriggeredDate = shouldGuardAgainstImmediateRetrigger(watchItem)
            watchItemDao.update(
                watchItem.reactivate(
                    currentPrice = currentPriceForReactivation(watchItem),
                    keepLastTriggeredDate = keepLastTriggeredDate
                )
            )
            syncWatchItemsAfterMutation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating watch item: ${e.message}")
            _actionError.tryEmit("Kunde inte uppdatera bevakningen")
            false
        }
    }

    private suspend fun syncWatchItemsAfterMutation() {
        // Reflect structural DB changes immediately, then refresh live prices in the background.
        loadWatchItems(forceShowStaleData = true)
        viewModelScope.launch {
            refreshWatchItems(showLoading = false)
        }
    }

    private suspend fun shouldGuardAgainstImmediateRetrigger(watchItem: WatchItem): Boolean =
        shouldGuardAgainstImmediateRetrigger(
            watchItem = watchItem,
            conditionCurrentlyMet = { conditionCurrentlyMet(watchItem) },
            isMarketOpen = { isMarketOpenForReactivation(watchItem) }
        )

    private suspend fun isMarketOpenForReactivation(watchItem: WatchItem): Boolean {
        val ticker = watchItem.ticker ?: watchItem.ticker1 ?: return true
        if (StockSearchResult.isCryptoSymbol(ticker)) return true

        val exchange = try {
            yahooFinanceService.getExchange(ticker)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch exchange for reactivation guard: ${e.message}")
            null
        }

        return StockMarketScheduler.isMarketOpenForSymbol(ticker, exchange)
    }

    /** Hämtar live-data för en bevakning; vid fel behålls senast kända värden markerade som inaktuella. */
    private suspend fun fetchLiveState(item: WatchItem, previousLive: Map<Int, LiveWatchData>): WatchItemUiState {
        val now = System.currentTimeMillis()
        return try {
            when (item.watchType) {
                is WatchType.PricePair -> {
                    if (item.ticker1 != null && item.ticker2 != null) {
                        Log.d(TAG, "Fetching prices for pair watch item")
                        val price1 = yahooFinanceService.getStockPrice(item.ticker1)
                        val price2 = yahooFinanceService.getStockPrice(item.ticker2)
                        if (price1 != null && price2 != null) {
                            Log.d(TAG, "Fetched prices for pair watch item")
                            WatchItemUiState(item, LiveWatchData(currentPrice1 = price1, currentPrice2 = price2, lastUpdatedAt = now))
                        } else {
                            Log.w(TAG, "Could not get prices for pair watch item")
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        WatchItemUiState(item)
                    }
                }
                is WatchType.PriceTarget -> {
                    if (item.ticker != null) {
                        Log.d(TAG, "Fetching price and daily change for price target watch item")
                        val price = yahooFinanceService.getStockPrice(item.ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                        if (price != null) {
                            Log.d(TAG, "Fetched price for price target watch item")
                            WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        } else {
                            Log.w(TAG, "Could not get price for price target watch item")
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        WatchItemUiState(item)
                    }
                }
                is WatchType.KeyMetrics -> {
                    if (item.ticker != null) {
                        val keyMetrics = item.watchType
                        Log.d(TAG, "Fetching key metric and price for key metrics watch item")
                        try {
                            val metricValue = yahooFinanceService.getKeyMetric(item.ticker, keyMetrics.metricType)
                            val price = yahooFinanceService.getStockPrice(item.ticker)
                            val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                            Log.d(TAG, "Key metric request completed")
                            if (metricValue != null) {
                                Log.d(TAG, "Fetched key metric value for key metrics watch item")
                                WatchItemUiState(item, LiveWatchData(
                                    currentMetricValue = metricValue,
                                    metricValueAtCreation = metricValue,
                                    currentPrice = price ?: 0.0,
                                    currentDailyChangePercent = changePercent,
                                    lastUpdatedAt = now
                                ))
                            } else if (price != null) {
                                WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                            } else {
                                Log.w(TAG, "Could not get metric value or price for key metrics watch item")
                                WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception while fetching key metric: ${e.message}", e)
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        Log.w(TAG, "Ticker is null for key metrics watch item")
                        WatchItemUiState(item)
                    }
                }
                is WatchType.ATHBased -> {
                    if (item.ticker != null) {
                        Log.d(TAG, "Fetching drawdown high, price and daily change for watch item")
                        val high = when (item.watchType.reference) {
                            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> yahooFinanceService.getATH(item.ticker)
                            WatchType.HighReference.ALL_TIME_HIGH -> yahooFinanceService.getAllTimeHigh(item.ticker)
                        }
                        val price = yahooFinanceService.getStockPrice(item.ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                        if (high != null && price != null && high > 0.0) {
                            Log.d(TAG, "Fetched drawdown data for watch item")
                            val effectiveHigh = if (price > high) price else high
                            WatchItemUiState(item, LiveWatchData(
                                currentATH = effectiveHigh,
                                currentPrice = price,
                                currentDropPercentage = ((effectiveHigh - price) / effectiveHigh) * 100,
                                currentDropAbsolute = effectiveHigh - price,
                                currentDailyChangePercent = changePercent,
                                lastUpdatedAt = now
                            ))
                        } else {
                            Log.w(TAG, "Could not get drawdown high or price for watch item")
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        WatchItemUiState(item)
                    }
                }
                is WatchType.PriceRange -> {
                    if (item.ticker != null) {
                        Log.d(TAG, "Fetching price and daily change for range watch item")
                        val price = yahooFinanceService.getStockPrice(item.ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                        if (price != null) {
                            WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        } else {
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        WatchItemUiState(item)
                    }
                }
                is WatchType.DailyMove -> {
                    if (item.ticker != null) {
                        Log.d(TAG, "Fetching price and daily change for daily move watch item")
                        val price = yahooFinanceService.getStockPrice(item.ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                        if (price != null) {
                            WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        } else {
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        WatchItemUiState(item)
                    }
                }
                is WatchType.InsiderBuy -> {
                    WatchItemUiState(item, LiveWatchData(lastUpdatedAt = now))
                }
                is WatchType.Combined -> {
                    if (item.ticker != null) {
                        Log.d(TAG, "Fetching price and daily change for combined alert")
                        val price = yahooFinanceService.getStockPrice(item.ticker)
                        val changePercent = yahooFinanceService.getDailyChangePercent(item.ticker)
                        if (price != null) {
                            WatchItemUiState(item, LiveWatchData(currentPrice = price, currentDailyChangePercent = changePercent, lastUpdatedAt = now))
                        } else {
                            Log.w(TAG, "Could not get price for combined alert")
                            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
                        }
                    } else {
                        Log.w(TAG, "Combined alert has no ticker set")
                        WatchItemUiState(item)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching prices for watch item ${item.id}: ${e.message}")
            WatchItemUiState(item, previousLive[item.id].asUpdateFailed())
        }
    }

    /** Delresultat: klara bevakningar får nya värden, övriga behåller senast kända (i ursprunglig ordning). */
    private fun mergeWithPrevious(
        items: List<WatchItem>,
        results: Array<WatchItemUiState?>,
        previousLive: Map<Int, LiveWatchData>
    ): List<WatchItemUiState> = items.mapIndexed { index, item ->
        results[index] ?: WatchItemUiState(item, previousLive[item.id] ?: LiveWatchData())
    }

    /**
     * Senast kända färska kurs för [symbol] ur listans live-data, så att aktiedetaljen kan visa den
     * direkt. null om ingen bevakning på aktien har en lyckad kurs.
     */
    fun lastKnownQuote(symbol: String, companyName: String? = null): InitialQuote? {
        val items = (_watchItemUiState.value as? UiState.Success<List<WatchItemUiState>>)?.data ?: return null
        val match = items
            .filter { it.item.ticker == symbol && it.live.currentPrice > 0.0 && !it.live.updateFailed }
            .maxByOrNull { it.live.lastUpdatedAt }
            ?: return null
        return InitialQuote(
            price = match.live.currentPrice,
            dailyChangePercent = match.live.currentDailyChangePercent,
            companyName = companyName ?: match.item.companyName,
            updatedAt = match.live.lastUpdatedAt
        )
    }

    private fun currentLiveById(): Map<Int, LiveWatchData> =
        (_watchItemUiState.value as? UiState.Success<List<WatchItemUiState>>)
            ?.data
            ?.associate { it.item.id to it.live }
            .orEmpty()

    /**
     * Utvärderar larmets villkor mot senaste live-data i UI-tillståndet.
     * @return true/false om det går att avgöra, annars null.
     */
    private fun conditionCurrentlyMet(watchItem: WatchItem): Boolean? {
        val uiState = (_watchItemUiState.value as? UiState.Success<List<WatchItemUiState>>)
            ?.data
            ?.firstOrNull { it.item.id == watchItem.id }
            ?: return null
        if (uiState.live.lastUpdatedAt == 0L || uiState.live.updateFailed) return null
        return uiState.hasLiveTriggerCondition()
    }

    private suspend fun currentPriceForReactivation(watchItem: WatchItem): Double? {
        if (watchItem.watchType !is WatchType.PriceTarget) return null
        val livePrice = (_watchItemUiState.value as? UiState.Success<List<WatchItemUiState>>)
            ?.data
            ?.firstOrNull { it.item.id == watchItem.id }
            ?.live
            ?.currentPrice
            ?.takeIf { it > 0.0 }
        if (livePrice != null) return livePrice

        val ticker = watchItem.ticker ?: watchItem.ticker1 ?: return null
        return try {
            yahooFinanceService.getStockPrice(ticker)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch current price for reactivation: ${e.message}")
            null
        }
    }

    suspend fun exportData(): String {
        val watchItems = watchItemDao.getAllWatchItems()
        val stockPairs = stockPairDao.getAllStockPairs()
        return BackupManager.exportToJson(watchItems, stockPairs)
    }

    suspend fun importData(json: String): ImportResult {
        return try {
            val data = BackupManager.importFromJson(json)
            watchItemDao.deleteAllWatchItems()
            stockPairDao.deleteAllStockPairs()
            data.watchItems.forEach { watchItemDao.insertWatchItem(it) }
            data.stockPairs.forEach { stockPairDao.insertStockPair(it) }
            ImportResult.Success(data.watchItems.size, data.stockPairs.size)
        } catch (e: Exception) {
            ImportResult.Error(BackupManager.importErrorMessage(e))
        }
    }

    suspend fun syncAfterImport() {
        // Importen ska uppföra sig som en kallstart: visa den importerade databasen direkt,
        // och hämta livevärden utan att lämna UI:t i ett laddningsläge.
        loadWatchItems(forceShowStaleData = true)
        refreshWatchItems(showLoading = false)
    }

    sealed class ImportResult {
        data class Success(val watchCount: Int, val pairCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val PARTIAL_EMIT_INTERVAL_MS = 250L
    }
} 
