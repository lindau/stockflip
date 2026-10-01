package com.stockflip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflip.MarketMover
import com.stockflip.MarketMoversService
import com.stockflip.MoverList
import com.stockflip.MoverMarket
import com.stockflip.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Listor över heta aktier ("Heta just nu" på Marknad-fliken). */
open class MoversViewModel(private val service: MarketMoversService) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<MarketMover>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MarketMover>>> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var lastRequest: Pair<MoverMarket, MoverList>? = null

    /** Hämtar vald lista. Ett äldre pågående anrop avbryts så att det inte skriver över ett nyare resultat. */
    fun load(market: MoverMarket, list: MoverList) {
        lastRequest = market to list
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val movers = service.getMarketMovers(market, list)
                _state.value = if (movers != null) UiState.Success(movers) else UiState.Error("Kunde inte hämta listan")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState.Error("Kunde inte hämta listan")
            }
        }
    }

    fun retry() {
        val (market, list) = lastRequest ?: return
        load(market, list)
    }
}
