package com.stockflip.ui.watchlist

import com.stockflip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stockflip.ui.components.EmptyState
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.SkeletonRow
import com.stockflip.ui.components.WatchRow
import com.stockflip.ui.stockdetail.AlertAction
import com.stockflip.ui.stockdetail.alertActionFor
import com.stockflip.ui.theme.Space

/**
 * Startsidan: en platt lista utan kort. Utlösta överst, sedan väntande. Tillståndslös —
 * värden och händelser kommer från värden (kopplas mot `MainViewModel` i nästa steg).
 *
 * @param onDelete anropas när en rad svepts bort; värden raderar och visar ångra-snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchlistScreen(
    sections: WatchListSections,
    stockSections: StockListSections = StockListSections(emptyList(), emptyList()),
    view: WatchView = WatchView.WATCHES,
    onViewChange: (WatchView) -> Unit = {},
    isLoading: Boolean,
    isRefreshing: Boolean,
    loadError: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: WatchSort = WatchSort.CREATED,
    onSortChange: (WatchSort) -> Unit = {},
    onRefresh: () -> Unit,
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
    onAddWatch: () -> Unit,
    onAddPair: () -> Unit,
    onAddCombined: () -> Unit,
    lastUpdated: String? = null,
    onRowAction: (WatchRowModel, AlertAction) -> Unit = { _, _ -> },
    onReactivateAll: () -> Unit = {},
    sparklines: Map<String, List<Double>> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.screenH, end = Space.md, top = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.watchlist_bevakningar), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.watchlist_uppdatera)) }
                var sortOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.watchlist_sortera)) }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        WatchSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                trailingIcon = { if (option == sort) Icon(Icons.Outlined.Check, contentDescription = null) },
                                onClick = { sortOpen = false; onSortChange(option) },
                            )
                        }
                    }
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.watchlist_ny_bevakning)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.watchlist_aktie_sok_i_marknad)) }, onClick = { menuOpen = false; onAddWatch() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.watchlist_aktiepar)) }, onClick = { menuOpen = false; onAddPair() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.watchlist_kombinerad)) }, onClick = { menuOpen = false; onAddCombined() })
                    }
                }
            }
        }
        lastUpdated?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.screenH))
        }
        ViewSwitcher(view, onViewChange)
        SearchField(query, onQueryChange)
        val listEmpty = if (view == WatchView.WATCHES) sections.isEmpty else stockSections.isEmpty

        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && listEmpty -> Column { repeat(6) { SkeletonRow() } }
                listEmpty && loadError != null ->
                    EmptyState(loadError, actionLabel = stringResource(R.string.watchlist_forsok_igen), onAction = onRefresh)
                listEmpty && query.isNotBlank() -> EmptyState("Inga träffar för \"${query.trim()}\".")
                listEmpty -> EmptyState(stringResource(R.string.watchlist_inga_bevakningar_an), actionLabel = stringResource(R.string.watchlist_ny_bevakning), onAction = onAddWatch)
                view == WatchView.STOCKS -> StockList(stockSections, sparklines, onRowClick)
                else -> WatchList(sections, sparklines, onRowClick, onDelete, onRowAction, onReactivateAll)
            }
        }
    }
}

private fun WatchSort.labelRes(): Int = when (this) {
    WatchSort.CREATED -> R.string.watchlist_sort_skapad
    WatchSort.NAME -> R.string.watchlist_sort_namn
    WatchSort.CHANGE_DESC -> R.string.watchlist_sort_uppgang
    WatchSort.CHANGE_ASC -> R.string.watchlist_sort_nedgang
    WatchSort.PRICE_DESC -> R.string.watchlist_sort_pris
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitcher(view: WatchView, onViewChange: (WatchView) -> Unit) {
    val options = listOf(WatchView.WATCHES to R.string.watchlist_vy_bevakningar, WatchView.STOCKS to R.string.watchlist_vy_aktier)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.sm)) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = view == option,
                onClick = { onViewChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/** Aktievyn: en rad per aktie (tryck öppnar aktiedetaljen), sedan aktiepar (öppnar pardetaljen). */
@Composable
private fun StockList(
    sections: StockListSections,
    sparklines: Map<String, List<Double>>,
    onRowClick: (WatchRowModel) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (sections.stocks.isNotEmpty()) {
            item(key = "h-stocks") { SectionLabel(stringResource(R.string.watchlist_vy_aktier), count = sections.stocks.size) }
            items(sections.stocks, key = { "s-${it.symbol}" }) { row -> StockRow(row, row != sections.stocks.first(), sparklines[row.symbol], onRowClick) }
        }
        if (sections.pairs.isNotEmpty()) {
            item(key = "h-pairs") { SectionLabel(stringResource(R.string.watchlist_aktiepar), count = sections.pairs.size) }
            items(sections.pairs, key = { "p-${it.id}" }) { row -> StockRow(row, row != sections.pairs.first(), null, onRowClick) }
        }
        item(key = "end") { Box(Modifier.height(Space.xxl)) }
    }
}

@Composable
private fun StockRow(row: WatchRowModel, showDivider: Boolean, sparkline: List<Double>?, onRowClick: (WatchRowModel) -> Unit) {
    WatchRow(
        title = row.title,
        subtitle = row.subtitle,
        price = row.price,
        priceValue = row.priceValue,
        change = row.change,
        changePositive = row.changePositive,
        triggered = row.triggered,
        onClick = { onRowClick(row) },
        showDivider = showDivider,
        sparkline = sparkline,
        staleLabel = row.staleLabel,
        hasNote = row.hasNote,
        hasPodcast = row.hasPodcast,
        logoSymbol = row.symbol,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.watchlist_sok_bland_bevakningar)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.sm),
    )
}

@Composable
private fun WatchList(
    sections: WatchListSections,
    sparklines: Map<String, List<Double>>,
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
    onRowAction: (WatchRowModel, AlertAction) -> Unit,
    onReactivateAll: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (sections.triggered.isNotEmpty()) {
            item(key = "h-triggered") {
                Box(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.watchlist_utlosta), count = sections.triggered.size)
                    if (sections.triggered.any { it.reactivatable }) {
                        TextButton(
                            onClick = onReactivateAll,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Space.sm),
                        ) { Text(stringResource(R.string.watchlist_aterstall_alla)) }
                    }
                }
            }
            items(sections.triggered, key = { it.id }) { row ->
                SwipeableRow(row, showDivider = row != sections.triggered.first(), sparklines[row.symbol], onRowClick, onDelete, onRowAction)
            }
        }
        if (sections.waiting.isNotEmpty()) {
            item(key = "h-waiting") { SectionLabel(stringResource(R.string.watchlist_vantar), count = sections.waiting.size) }
            items(sections.waiting, key = { it.id }) { row ->
                SwipeableRow(row, showDivider = row != sections.waiting.first(), sparklines[row.symbol], onRowClick, onDelete, onRowAction)
            }
        }
        item(key = "end") { Box(Modifier.height(Space.xxl)) }
    }
}

/**
 * Svep åt höger = pausa/aktivera/återaktivera (beroende på radens läge, se [alertActionFor]); svep åt vänster = ta bort.
 * Åtgärden vid höger-svep lämnar raden kvar (den snäpper tillbaka), så listan ändras bara av det som händer i datat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    row: WatchRowModel,
    showDivider: Boolean,
    sparkline: List<Double>?,
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
    onRowAction: (WatchRowModel, AlertAction) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val deleteLabel = stringResource(R.string.watchlist_ta_bort)
    val action = alertActionFor(triggered = row.triggered, isActive = !row.paused)
    // confirmValueChange sparas av rememberSwipeToDismissBoxState vid första komponeringen; utan dessa läser den
    // gamla värden (t.ex. "Pausa" trots att raden redan är pausad).
    val currentRow by rememberUpdatedState(row)
    val currentAction by rememberUpdatedState(action)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnRowAction by rememberUpdatedState(onRowAction)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnDelete(currentRow)
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnRowAction(currentRow, currentAction)
                    false
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val toRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize()
                    .background(if (toRight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Space.screenH),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (toRight) {
                    Text(action.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.watchlist_ta_bort), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        Box(
            Modifier.background(MaterialTheme.colorScheme.background).semantics {
                // Svepgesterna har inget tillgängligt alternativ annars.
                customActions = listOf(
                    CustomAccessibilityAction(action.label) { onRowAction(row, action); true },
                    CustomAccessibilityAction(deleteLabel) { onDelete(row); true },
                )
            },
        ) {
            WatchRow(
                title = row.title,
                subtitle = row.condition,
                statusSuffix = row.statusSuffix,
                price = row.price,
                priceValue = row.priceValue,
                change = row.change,
                changePositive = row.changePositive,
                triggered = row.triggered,
                onClick = { onRowClick(row) },
                showDivider = showDivider,
                sparkline = sparkline,
                staleLabel = row.staleLabel,
                hasNote = row.hasNote,
                hasPodcast = row.hasPodcast,
                logoSymbol = row.symbol,
            )
        }
    }
}
