package com.stockflip.ui.watchlist

import androidx.compose.material3.SnackbarDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import com.stockflip.ui.stockdetail.AlertAction
import com.stockflip.toUserMessage
import com.stockflip.isTriggeredForDisplay
import com.stockflip.WatchType
import com.stockflip.repository.StockRepository
import com.stockflip.ui.createwatch.CombinedWatchSheet
import com.stockflip.ui.createwatch.PairWatchSheet
import com.stockflip.ui.createwatch.decomposeCombined
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
    onOpenPair: (watchItemId: Int) -> Unit,
    onAddWatch: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val state by viewModel.watchItemUiState.collectAsState()
    val refreshing by viewModel.watchItemsRefreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val repository = remember { StockRepository() }
    var sheet by remember { mutableStateOf<WatchSheet?>(null) }

    val items: List<WatchItemUiState> = (state as? UiState.Success)?.data.orEmpty()
    var sparklines by remember { mutableStateOf<Map<String, List<Double>>>(emptyMap()) }
    val symbols = items.mapNotNull { it.item.ticker?.takeIf { _ -> it.item.watchType !is com.stockflip.WatchType.PricePair } }.distinct()
    LaunchedEffect(symbols) { if (symbols.isNotEmpty()) sparklines = SparklineStore.load(symbols, com.stockflip.YahooFinanceService) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var triggerTimes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    LaunchedEffect(items.count { it.isTriggeredForDisplay() }) {
        triggerTimes = try {
            com.stockflip.StockPairDatabase.getDatabase(context).triggerHistoryDao().getLatestPerWatchItem().associate { it.watchItemId to it.triggeredAt }
        } catch (e: Exception) { emptyMap() }
    }
    val noted by viewModel.notedTickers.collectAsState()
    val mentionedAll by viewModel.mentionedTickers.collectAsState()
    // Poddikonen visas bara när poddfunktionen är konfigurerad och påslagen (som poddsektionen på aktiedetaljen).
    val podcastActive = com.stockflip.BuildConfig.PODCAST_ANALYSIS_BASE_URL.isNotBlank() &&
        com.stockflip.PodcastObservationSettings.isEnabled(context)
    val mentioned = if (podcastActive) mentionedAll else emptySet()
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var sort by remember {
        mutableStateOf(
            try { WatchSort.valueOf(prefs.getString("watchlist_sort", null) ?: "") } catch (e: Exception) { WatchSort.CREATED }
        )
    }
    var view by remember {
        mutableStateOf(
            try { WatchView.valueOf(prefs.getString("watchlist_view", null) ?: "") } catch (e: Exception) { WatchView.WATCHES }
        )
    }
    val stockSections by remember(items, query, triggerTimes, noted, mentioned, sort) {
        derivedStateOf { stockSectionsFor(items, query, triggerTimes, notedTickers = noted, mentionedTickers = mentioned, sort = sort) }
    }
    val sections by remember(items, query, triggerTimes, noted, mentioned, sort) {
        derivedStateOf { sectionsFor(items, query, triggerTimes, notedTickers = noted, mentionedTickers = mentioned, sort = sort) }
    }

    // Som MainActivity förut: visa sparad data direkt, uppdatera sedan kurserna tyst i bakgrunden.
    LaunchedEffect(Unit) {
        viewModel.loadWatchItems(forceShowStaleData = true)
        viewModel.refreshWatchItems(showLoading = false)
    }
    LaunchedEffect(Unit) { viewModel.actionError.collect { snackbarHostState.showSnackbar(it) } }

    WatchlistScreen(
        sections = sections,
        stockSections = stockSections,
        view = view,
        onViewChange = { view = it; prefs.edit().putString("watchlist_view", it.name).apply() },
        isLoading = state is UiState.Loading,
        isRefreshing = refreshing,
        loadError = (state as? UiState.Error)?.message,
        query = query,
        onQueryChange = { query = it },
        sort = sort,
        onSortChange = { sort = it; prefs.edit().putString("watchlist_sort", it.name).apply() },
        onRefresh = { scope.launch { viewModel.refreshWatchItems(showLoading = false, userInitiated = true) } },
        onRowClick = { row ->
            val item = items.firstOrNull { it.item.id == row.id }?.item
            when {
                // Aktieraden representerar flera bevakningar: öppna alltid aktiedetaljen.
                view == WatchView.STOCKS && !row.isPair -> row.symbol?.let(onOpenStock)
                item?.watchType is WatchType.PricePair -> onOpenPair(item.id)
                item?.watchType is WatchType.Combined -> {
                    if (decomposeCombined((item.watchType as WatchType.Combined).expression) != null) sheet = WatchSheet.Combined(item)
                    else row.symbol?.let(onOpenStock)
                }
                else -> row.symbol?.let(onOpenStock)
            }
        },
        onDelete = { row ->
            val target = items.firstOrNull { it.item.id == row.id }?.item ?: return@WatchlistScreen
            scope.launch { deleteWithUndo(viewModel, snackbarHostState, target, row.title) }
        },
        onRowAction = { row, action ->
            val target = items.firstOrNull { it.item.id == row.id }?.item
            if (target != null) scope.launch {
                when (action) {
                    AlertAction.Reactivate -> {
                        val result = try { viewModel.reactivateWatchItem(target) } catch (e: Exception) { null }
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(result?.toUserMessage() ?: "Kunde inte återaktivera bevakningen")
                    }
                    AlertAction.Pause, AlertAction.Resume -> {
                        val activate = action == AlertAction.Resume
                        if (viewModel.toggleWatchItemActive(target, activate)) {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(if (activate) "Bevakningen är aktiv" else "Bevakningen är pausad")
                        }
                    }
                }
            }
        },
        onReactivateAll = {
            scope.launch {
                val count = try { viewModel.reactivateAllTriggered() } catch (e: Exception) { -1 }
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    when {
                        count < 0 -> "Kunde inte återaktivera bevakningarna"
                        count == 0 -> "Inga utlösta bevakningar att återaktivera"
                        count == 1 -> "1 bevakning återställd"
                        else -> "$count bevakningar återställda"
                    }
                )
            }
        },
        onAddWatch = onAddWatch,
        onAddPair = { sheet = WatchSheet.Pair(null) },
        onAddCombined = { sheet = WatchSheet.Combined(null) },
        sparklines = sparklines,
        lastUpdated = lastUpdatedLabel(items) { SimpleDateFormat("HH:mm", Locale("sv", "SE")).format(Date(it)) },
        modifier = modifier,
    )

    val save: suspend (WatchItem) -> String? = { item ->
        val ok = if (item.id == 0) viewModel.addWatchItem(item) else viewModel.updateWatchItem(item)
        if (ok) { sheet = null; null } else "Kunde inte spara bevakningen. Försök igen."
    }
    when (val s = sheet) {
        is WatchSheet.Pair -> PairWatchSheet(repository, s.item, { sheet = null }, save)
        is WatchSheet.Combined -> CombinedWatchSheet(repository, s.item, { sheet = null }, save)
        null -> Unit
    }
}

private sealed interface WatchSheet {
    data class Pair(val item: WatchItem?) : WatchSheet
    data class Combined(val item: WatchItem?) : WatchSheet
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
