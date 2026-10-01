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
import com.stockflip.MoverList
import com.stockflip.MoverMarket
import com.stockflip.StockSearchResult
import com.stockflip.viewmodel.MoversViewModel
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 250L

/** Kopplar [MarketScreen] mot [StockSearchViewModel] och senast sökta. */
@Composable
internal fun MarketRoute(
    viewModel: StockSearchViewModel,
    moversViewModel: MoversViewModel,
    onOpenStock: (symbol: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember { mutableStateOf(RecentSearches.load(context)) }
    val searchState by viewModel.searchState.collectAsState()
    val moversState by moversViewModel.state.collectAsState()
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var moverMarket by remember {
        mutableStateOf(try { MoverMarket.valueOf(prefs.getString("movers_market", null) ?: "") } catch (e: Exception) { MoverMarket.SWEDEN })
    }
    var moverList by remember {
        mutableStateOf(try { MoverList.valueOf(prefs.getString("movers_list", null) ?: "") } catch (e: Exception) { MoverList.GAINERS })
    }
    LaunchedEffect(moverMarket, moverList) {
        // Skydd mot en sparad kombination som inte finns (t.ex. Sverige + Trendar).
        if (!moverList.isAvailableFor(moverMarket)) { moverList = MoverList.GAINERS; return@LaunchedEffect } moversViewModel.load(moverMarket, moverList) }

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
        movers = MoversUi(
            market = moverMarket,
            list = moverList,
            state = moversState,
            onMarketChange = { market ->
                moverMarket = market
                // Trendar finns bara för USA; byt till uppgång när man går till Sverige.
                if (!moverList.isAvailableFor(market)) moverList = MoverList.GAINERS
                prefs.edit().putString("movers_market", market.name).putString("movers_list", moverList.name).apply()
            },
            onListChange = { moverList = it; prefs.edit().putString("movers_list", it.name).apply() },
            onOpen = { mover ->
                val result = StockSearchResult(
                    symbol = mover.symbol,
                    name = mover.name,
                    isSwedish = mover.symbol.endsWith(".ST"),
                )
                recent = RecentSearches.add(recent, result).also { RecentSearches.save(context, it) }
                onOpenStock(mover.symbol)
            },
            onRetry = { moversViewModel.retry() },
        ),
        onClearRecent = {
            recent = emptyList()
            RecentSearches.save(context, recent)
        },
        modifier = modifier,
    )
}
