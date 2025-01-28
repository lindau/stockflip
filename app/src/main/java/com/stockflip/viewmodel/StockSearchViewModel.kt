package com.stockflip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
open class StockSearchViewModel(
    private val repository: StockRepository
) : ViewModel() {
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Success(emptyList()))
    val searchState: StateFlow<SearchState> = _searchState

    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.length < 2) {
            _searchState.value = SearchState.Success(emptyList())
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            repository.searchStocks(query)
                .debounce(300)
                .catch { e ->
                    _searchState.value = SearchState.Error(e.message ?: "Unknown error", query)
                }
                .collect { state ->
                    _searchState.value = state
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
        searchJob?.cancel()
        _searchState.value = SearchState.Success(emptyList())
    }
} 