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

    fun search(query: String) {
        Log.d(TAG, "Starting search for query: $query")
        
        if (query.length < 2) {
            Log.d(TAG, "Query too short, clearing results")
            _searchState.value = SearchState.Success(emptyList())
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Executing search for query: $query")
                _searchState.value = SearchState.Loading
                
                repository.searchStocks(query)
                    .collect { state ->
                        Log.d(TAG, "Received search state: $state")
                        _searchState.value = state
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error during search: ${e.message}")
                _searchState.value = SearchState.Error(e.message ?: "Unknown error", query)
            }
        }
    }

    fun retry() {
        val currentState = _searchState.value
        if (currentState is SearchState.Error) {
            search(currentState.lastQuery ?: "")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cancelling search job")
        searchJob?.cancel()
        _searchState.value = SearchState.Success(emptyList())
    }
} 