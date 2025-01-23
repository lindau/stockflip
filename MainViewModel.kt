// MainViewModel.kt
class MainViewModel(
    private val stockPairDao: StockPairDao,
    private val yahooFinanceService: YahooFinanceService
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateOnly()
    
    init {
        viewModelScope.launch {
            stockPairDao.getAllStockPairs()
                .catch { _uiState.value = UiState.Error(it.message ?: "Unknown error") }
                .collect { pairs ->
                    _uiState.value = UiState.Success(pairs)
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

    fun deleteStockPair(pair: StockPairEntity) {
        viewModelScope.launch {
            stockPairDao.deleteStockPair(pair)
        }
    }
}