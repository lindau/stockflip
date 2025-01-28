package com.stockflip.repository

import com.stockflip.StockSearchResult

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val results: List<StockSearchResult>) : SearchState()
    data class Error(val message: String, val lastQuery: String? = null) : SearchState()
} 
