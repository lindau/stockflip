package com.stockflip.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

open class StockSearchViewModel(
    private val repository: StockRepository
) : ViewModel() {
    private val TAG = "StockSearchViewModel"
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Success(emptyList()))
    val searchState: StateFlow<SearchState> = _searchState

    private var searchJob: Job? = null

    fun search(query: String, includeCrypto: Boolean = true) {
        Log.d(TAG, "Starting stock search (includeCrypto: $includeCrypto)")
        
        if (query.length < 2) {
            Log.d(TAG, "Query too short, clearing results")
            _searchState.value = SearchState.Success(emptyList())
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Executing stock search")
                _searchState.value = SearchState.Loading
                
                repository.searchStocks(query, includeCrypto)
                    .collect { state ->
                        when (state) {
                            is SearchState.Loading -> Unit
                            is SearchState.Success -> Log.d(TAG, "Stock search completed with ${state.results.size} results")
                            is SearchState.Error -> Log.w(TAG, "Stock search failed")
                        }
                        _searchState.value = state
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error during stock search: ${e.message}")
                _searchState.value = SearchState.Error(e.message ?: "Unknown error", query)
            }
        }
    }

    fun retry() {
        val currentState = _searchState.value
        if (currentState is SearchState.Error) {
            search(currentState.lastQuery ?: "", includeCrypto = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cancelling search job")
        searchJob?.cancel()
        _searchState.value = SearchState.Success(emptyList())
    }
} 
