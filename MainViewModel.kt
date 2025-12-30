import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val stockPairDao: StockPairDao,
    private val stockWatchDao: StockWatchDao,
    private val yahooFinanceService: YahooFinanceService
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateOnly()
    
    init {
        viewModelScope.launch {
            combine(
                stockPairDao.getAllStockPairs(),
                stockWatchDao.getAllStockWatches()
            ) { pairs, watches ->
                UiState.Success(pairs, watches)
            }.catch { 
                _uiState.value = UiState.Error(it.message ?: "Unknown error")
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    suspend fun validateAndAddStockPair(
        stockA: String,
        stockB: String,
        priceDifference: Double,
        notifyWhenEqual: Boolean
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val sanitizedA = stockA.trim().uppercase()
                val sanitizedB = stockB.trim().uppercase()
                
                if (priceDifference < 0) {
                    return@withContext Result.failure(IllegalArgumentException("Price difference must be positive"))
                }
                
                val (priceA, priceB) = coroutineScope {
                    val deferredA = async { yahooFinanceService.getStockPrice(sanitizedA) }
                    val deferredB = async { yahooFinanceService.getStockPrice(sanitizedB) }
                    Pair(deferredA.await(), deferredB.await())
                }
                
                if (priceA == null || priceB == null) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid stock symbol(s)"))
                }
                
                stockPairDao.insertStockPair(
                    StockPairEntity(
                        stockA = sanitizedA,
                        stockB = sanitizedB,
                        priceDifference = priceDifference,
                        notifyWhenEqual = notifyWhenEqual
                    )
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun validateAndAddStockWatch(
        symbol: String,
        watchCriteria: WatchCriteria,
        notifyOnTrigger: Boolean
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val sanitizedSymbol = symbol.trim().uppercase()

                // Verify stock exists
                val currentPrice = yahooFinanceService.getStockPrice(sanitizedSymbol)
                
                if (currentPrice == null) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid stock symbol"))
                }

                // Validate and fetch data based on criteria type
                val ath: Double
                val dailyHigh: Double?
                val peRatio: Double?
                val psRatio: Double?

                when (watchCriteria) {
                    is WatchCriteria.PriceTargetCriteria -> {
                        // No additional data needed
                        ath = 0.0
                        dailyHigh = null
                        peRatio = null
                        psRatio = null
                    }
                    is WatchCriteria.PERatioCriteria -> {
                        peRatio = yahooFinanceService.getPERatio(sanitizedSymbol)
                        if (peRatio == null) {
                            return@withContext Result.failure(IllegalArgumentException("Could not fetch P/E ratio for this stock"))
                        }
                        ath = 0.0
                        dailyHigh = null
                        psRatio = null
                    }
                    is WatchCriteria.PSRatioCriteria -> {
                        psRatio = yahooFinanceService.getPSRatio(sanitizedSymbol)
                        if (psRatio == null) {
                            return@withContext Result.failure(IllegalArgumentException("Could not fetch P/S ratio for this stock"))
                        }
                        ath = 0.0
                        dailyHigh = null
                        peRatio = null
                    }
                    is WatchCriteria.ATHDropCriteria -> {
                        ath = yahooFinanceService.getATH(sanitizedSymbol) ?: currentPrice
                        dailyHigh = null
                        peRatio = null
                        psRatio = null
                    }
                    is WatchCriteria.DailyHighDropCriteria -> {
                        dailyHigh = yahooFinanceService.getDailyHigh(sanitizedSymbol) ?: currentPrice
                        ath = 0.0
                        peRatio = null
                        psRatio = null
                    }
                }

                // For backward compatibility, convert ATHDropCriteria to old format if needed
                val dropValue = when (watchCriteria) {
                    is WatchCriteria.ATHDropCriteria -> watchCriteria.dropPercentage
                    is WatchCriteria.DailyHighDropCriteria -> watchCriteria.dropPercentage
                    else -> 0.0
                }
                val isPercentage = watchCriteria is WatchCriteria.ATHDropCriteria || watchCriteria is WatchCriteria.DailyHighDropCriteria

                stockWatchDao.insertStockWatch(
                    StockWatchEntity(
                        symbol = sanitizedSymbol,
                        stockName = sanitizedSymbol,
                        dropValue = dropValue,
                        isPercentage = isPercentage,
                        notifyOnTrigger = notifyOnTrigger,
                        ath = ath,
                        watchCriteria = watchCriteria
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun deleteStockPair(pair: StockPairEntity) {
        viewModelScope.launch {
            stockPairDao.deleteStockPair(pair)
        }
    }

    fun deleteStockWatch(watch: StockWatchEntity) {
        viewModelScope.launch {
            stockWatchDao.deleteStockWatch(watch)
        }
    }
}