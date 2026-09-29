package com.stockflip.ui.market

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stockflip.StockSearchResult
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 250L

/** Kopplar [MarketScreen] mot [StockSearchViewModel] och senast sökta. */
@Composable
internal fun MarketRoute(
    viewModel: StockSearchViewModel,
    onOpenStock: (symbol: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember { mutableStateOf(RecentSearches.load(context)) }
    val searchState by viewModel.searchState.collectAsState()

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        viewModel.search(query.trim())
    }

    MarketScreen(
        query = query,
        content = marketContentFor(query, searchState, recent),
        onQueryChange = { query = it },
        onResultClick = { result: StockSearchResult ->
            recent = RecentSearches.add(recent, result).also { RecentSearches.save(context, it) }
            onOpenStock(result.symbol)
        },
        onRetry = { viewModel.retry() },
        onClearRecent = {
            recent = emptyList()
            RecentSearches.save(context, recent)
        },
        modifier = modifier,
    )
}
