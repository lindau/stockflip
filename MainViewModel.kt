import kotlinx.coroutines.flow.combine

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
        dropValue: Double,
        isPercentage: Boolean,
        notifyOnTrigger: Boolean
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val sanitizedSymbol = symbol.trim().uppercase()
                
                if (dropValue < 0) {
                    return@withContext Result.failure(IllegalArgumentException("Drop value must be positive"))
                }

                // Verify stock exists and get ATH
                val currentPrice = yahooFinanceService.getStockPrice(sanitizedSymbol)
                
                if (currentPrice == null) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid stock symbol"))
                }
                
                val ath = yahooFinanceService.getATH(sanitizedSymbol) ?: 0.0

                stockWatchDao.insertStockWatch(
                    StockWatchEntity(
                        symbol = sanitizedSymbol,
                        stockName = sanitizedSymbol, // Could fetch name if API supported it easily
                        dropValue = dropValue,
                        isPercentage = isPercentage,
                        notifyOnTrigger = notifyOnTrigger,
                        ath = ath
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