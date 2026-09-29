package com.stockflip.ui.pairdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stockflip.PairDetailViewModel
import com.stockflip.R
import com.stockflip.UiState
import com.stockflip.WatchItem
import com.stockflip.repository.StockRepository
import com.stockflip.toUserMessage
import com.stockflip.ui.components.EmptyState
import com.stockflip.ui.components.SkeletonRow
import com.stockflip.ui.createwatch.PairWatchSheet
import com.stockflip.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Pardetalj: återanvänder [ClarityPairDetailPanel] (graf, spread, historik) i det nya skalet, med
 * återaktivera/ta bort för utlösta par och redigering i [PairWatchSheet].
 * [onSaveEdit] sparar den ändrade bevakningen och returnerar om det lyckades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairDetailRoute(
    viewModel: PairDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onSaveEdit: suspend (WatchItem) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pairState by viewModel.pairState.collectAsState()
    val chartState by viewModel.chartState.collectAsState()
    val period by viewModel.selectedPeriod.collectAsState()
    val history by viewModel.historyState.collectAsState()
    val scope = rememberCoroutineScope()
    val repository = remember { StockRepository() }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val failedText = stringResource(R.string.pair_delete_failed)
    val reactivateFailed = stringResource(R.string.pair_reactivate_failed)
    val deletedText = stringResource(R.string.pair_deleted)

    // Pull-to-refresh nollställs i både Success och Error (projektregel).
    LaunchedEffect(pairState) { if (pairState !is UiState.Loading) refreshing = false }
    LaunchedEffect(Unit) { viewModel.refreshFailed.collect { snackbarHostState.showSnackbar("Kunde inte uppdatera. Visar senast kända data.") } }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.pairdetail_tillbaka)) }
            Spacer(Modifier.weight(1f))
            if (pairState is UiState.Success) TextButton(onClick = { editing = true }) { Text(stringResource(R.string.pairdetail_redigera)) }
        }
        when (val state = pairState) {
            UiState.Loading -> Column { repeat(4) { SkeletonRow() } }
            is UiState.Error -> EmptyState(state.message, actionLabel = stringResource(R.string.pairdetail_forsok_igen), onAction = viewModel::refresh)
            is UiState.Success -> {
                val data = state.data
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            if (data.watchItem.isTriggered) {
                                Button(onClick = {
                                    scope.launch {
                                        val result = try { viewModel.reactivateAndReturnResult() } catch (e: Exception) { null }
                                        snackbarHostState.showSnackbar(result?.toUserMessage() ?: reactivateFailed)
                                    }
                                }) { Text(stringResource(R.string.pairdetail_ateraktivera)) }
                                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.pairdetail_ta_bort)) }
                            }
                        }
                        PairDetailScreen(
                            data = data,
                            chartData = (chartState as? UiState.Success)?.data,
                            selectedPeriod = period,
                            history = history,
                            onPeriodSelected = viewModel::selectPeriod,
                                        )
                        TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.pairdetail_ta_bort_aktiepar), color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (editing) {
                    PairWatchSheet(
                        repository = repository,
                        existing = data.watchItem,
                        onDismiss = { editing = false },
                        onSave = { item ->
                            if (onSaveEdit(item)) { editing = false; viewModel.refresh(); null }
                            else "Kunde inte spara bevakningen. Försök igen."
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.pairdetail_ta_bort_aktiepar_2)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (viewModel.deletePair()) { launch { snackbarHostState.showSnackbar(deletedText) }; onBack() }
                        else snackbarHostState.showSnackbar(failedText)
                    }
                }) { Text(stringResource(R.string.pairdetail_ta_bort)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.pairdetail_avbryt)) } },
        )
    }
}
