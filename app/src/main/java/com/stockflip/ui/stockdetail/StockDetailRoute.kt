package com.stockflip.ui.stockdetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.stockflip.toUserMessage
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stockflip.BollingerPoint
import com.stockflip.ChartIndicatorConfig
import com.stockflip.ChartIndicatorSettings
import com.stockflip.RsiPoint
import com.stockflip.SmaChartLevel
import com.stockflip.StockDetailViewModel
import com.stockflip.TechnicalIndicators
import com.stockflip.UiState
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import com.stockflip.supportsIndicators
import com.stockflip.ui.components.EmptyState
import com.stockflip.ui.createwatch.CreateWatchSheet
import com.stockflip.ui.createwatch.draftFrom
import com.stockflip.ui.components.SkeletonRow
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Kopplar [StockDetailScreen] mot [StockDetailViewModel]. Indikatorserier (SMA från aktiens
 * bevakningar, Bollinger, RSI) hämtas om när period, konfiguration eller bevakningar ändras;
 * `LaunchedEffect` avbryter en föregående hämtning automatiskt.
 */
@Composable
internal fun StockDetailRoute(
    viewModel: StockDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stockState by viewModel.stockDataState.collectAsState()
    val chartState by viewModel.chartState.collectAsState()
    val alertsState by viewModel.alertsState.collectAsState()
    val period by viewModel.selectedPeriod.collectAsState()
    var config by remember { mutableStateOf(ChartIndicatorSettings.load(context)) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WatchItemUiState?>(null) }
    val note by viewModel.noteState.collectAsState()
    var noteOpen by remember { mutableStateOf(false) }

    val alerts: List<WatchItemUiState> = (alertsState as? UiState.Success)?.data.orEmpty()
    val chartData = (chartState as? UiState.Success)?.data

    // Pull-to-refresh nollställs i både Success och Error (projektregel).
    LaunchedEffect(stockState) { if (stockState !is UiState.Loading) pullRefreshing = false }
    LaunchedEffect(Unit) { viewModel.refreshFailed.collect { snackbarHostState.showSnackbar("Kunde inte uppdatera. Visar senast kända data.") } }

    var indicators by remember { mutableStateOf(Indicators()) }
    LaunchedEffect(period, config, alerts.map { it.item.watchType }) {
        indicators = loadIndicators(viewModel, config, period.supportsIndicators(), period, smaPeriodsOf(alerts))
    }

    when (val state = stockState) {
        is UiState.Success -> {
        StockDetailScreen(
            data = state.data,
            chartData = chartData,
            selectedPeriod = period,
            alerts = alerts,
            isRefreshing = pullRefreshing,
            smaLevels = indicators.sma,
            bollingerPoints = indicators.bollinger,
            rsiPoints = indicators.rsi,
            indicatorConfig = config,
            onIndicatorConfigChange = { config = it; ChartIndicatorSettings.save(context, it) },
            onPeriodSelected = viewModel::selectPeriod,
            onRefresh = { pullRefreshing = true; viewModel.refresh() },
            onBack = onBack,
            onAddWatch = { editing = null; sheetOpen = true },
            onEditAlert = { alert ->
                // Typer som sheeten inte kan redigera (t.ex. prisintervall, kombinerad) får inte öppna ett tomt
                // "Ny bevakning"-formulär som sedan skulle skriva över den befintliga bevakningen.
                if (draftFrom(alert.item.watchType) == null) {
                    scope.launch { snackbarHostState.showSnackbar("Den här bevakningstypen kan inte redigeras här. Svep bort den i Bevakningar och skapa en ny.") }
                } else {
                    editing = alert; sheetOpen = true
                }
            },
            onAlertAction = { alert, action ->
                scope.launch {
                    when (action) {
                        AlertAction.Reactivate -> {
                            val result = try { viewModel.reactivateAlertAndReturnResult(alert.item) } catch (e: Exception) { null }
                            snackbarHostState.showSnackbar(result?.toUserMessage() ?: "Kunde inte återaktivera bevakningen")
                        }
                        AlertAction.Pause, AlertAction.Resume -> {
                            viewModel.toggleAlert(alert.item)
                            snackbarHostState.showSnackbar(if (action == AlertAction.Pause) "Bevakningen är pausad" else "Bevakningen är aktiv")
                        }
                    }
                }
            },
            note = note?.note,
            onEditNote = { noteOpen = true },
            modifier = modifier,
        )
        if (noteOpen) {
            NoteDialog(
                initial = note?.note.orEmpty(),
                onSave = { viewModel.saveNote(it); noteOpen = false },
                onDismiss = { noteOpen = false },
            )
        }
        if (sheetOpen) {
            val target = editing
            CreateWatchSheet(
                data = state.data,
                initial = target?.let { draftFrom(it.item.watchType) },
                onDismiss = { sheetOpen = false },
                fetchSma = { viewModel.getSma(it) },
                onSave = { type ->
                    val error = viewModel.saveWatch(type, state.data.companyName, target?.item)
                    if (error == null) sheetOpen = false
                    error
                },
            )
        }
        }
        is UiState.Error -> EmptyState(state.message, actionLabel = "Försök igen", onAction = viewModel::refresh, modifier = modifier)
        UiState.Loading -> Column(modifier) { repeat(6) { SkeletonRow() } }
    }
}

private data class Indicators(
    val sma: List<SmaChartLevel> = emptyList(),
    val bollinger: List<BollingerPoint> = emptyList(),
    val rsi: List<RsiPoint> = emptyList(),
)

private fun smaPeriodsOf(alerts: List<WatchItemUiState>): List<Int> =
    alerts.flatMap { state ->
        when (val wt = state.item.watchType) {
            is WatchType.PriceVsSma -> listOf(wt.period)
            is WatchType.SmaCrossover -> listOf(wt.shortPeriod, wt.longPeriod)
            else -> emptyList()
        }
    }.distinct().sorted()

private suspend fun loadIndicators(
    viewModel: StockDetailViewModel,
    config: ChartIndicatorConfig,
    supported: Boolean,
    period: com.stockflip.ChartPeriod,
    alertSmaPeriods: List<Int>,
): Indicators = coroutineScope {
    val smaPeriods = if (config.showSma) alertSmaPeriods else emptyList()
    val bands = async {
        if (config.showBollinger && supported) {
            viewModel.getBollingerSeries(TechnicalIndicators.DEFAULT_BOLLINGER_PERIOD, TechnicalIndicators.DEFAULT_BOLLINGER_STD_DEVS, period)
        } else null
    }
    val rsi = async {
        if (config.showRsi && supported) viewModel.getRsiSeries(TechnicalIndicators.DEFAULT_RSI_PERIOD, period) else null
    }
    val sma = smaPeriods.map { p -> async { p to viewModel.getSmaSeries(p, period) } }.awaitAll()
        .mapNotNull { (p, pts) -> pts?.takeIf { it.isNotEmpty() }?.let { SmaChartLevel(p, it) } }
    Indicators(sma, bands.await().orEmpty(), rsi.await().orEmpty())
}
