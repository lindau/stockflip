package com.stockflip.ui.watchlist

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.stockflip.MainViewModel
import com.stockflip.UiState
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import kotlinx.coroutines.launch

/**
 * Kopplar [WatchlistScreen] mot [MainViewModel]. `isRefreshing` nollställs alltid när
 * `watchItemsRefreshing` går tillbaka till false, oavsett Success eller Error.
 */
@Composable
internal fun WatchlistRoute(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenStock: (symbol: String) -> Unit,
    onAddWatch: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val state by viewModel.watchItemUiState.collectAsState()
    val refreshing by viewModel.watchItemsRefreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val items: List<WatchItemUiState> = (state as? UiState.Success)?.data.orEmpty()
    val sections by remember(items, query) { derivedStateOf { sectionsFor(items, query) } }

    LaunchedEffect(Unit) { viewModel.actionError.collect { snackbarHostState.showSnackbar(it) } }

    WatchlistScreen(
        sections = sections,
        isLoading = state is UiState.Loading,
        isRefreshing = refreshing,
        loadError = (state as? UiState.Error)?.message,
        query = query,
        onQueryChange = { query = it },
        onRefresh = { scope.launch { viewModel.refreshWatchItems(showLoading = false) } },
        onRowClick = { row -> row.symbol?.let(onOpenStock) },
        onDelete = { row ->
            val target = items.firstOrNull { it.item.id == row.id }?.item ?: return@WatchlistScreen
            scope.launch { deleteWithUndo(viewModel, snackbarHostState, target, row.title) }
        },
        onAddWatch = onAddWatch,
        modifier = modifier,
    )
}

private suspend fun deleteWithUndo(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    item: WatchItem,
    title: String,
) {
    if (!viewModel.deleteWatchItem(item)) return
    snackbarHostState.currentSnackbarData?.dismiss()
    val result = snackbarHostState.showSnackbar(
        message = "Tog bort bevakning för $title",
        actionLabel = "Ångra",
        duration = SnackbarDuration.Long,
    )
    if (result == SnackbarResult.ActionPerformed) viewModel.addWatchItem(item)
}
