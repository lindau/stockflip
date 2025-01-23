// UiState.kt
sealed class UiState {
    object Loading : UiState()
    data class Success(val pairs: List<StockPairEntity>) : UiState()
    data class Error(val message: String) : UiState()
}