package com.stockflip.ui.watchlist

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.stockflip.ui.components.EmptyState
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.SkeletonRow
import com.stockflip.ui.components.WatchRow
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
    isLoading: Boolean,
    isRefreshing: Boolean,
    loadError: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
    onAddWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.screenH, end = Space.md, top = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Bevakningar", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Uppdatera") }
        }
        SearchField(query, onQueryChange)

        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && sections.isEmpty -> Column { repeat(6) { SkeletonRow() } }
                sections.isEmpty && loadError != null ->
                    EmptyState(loadError, actionLabel = "Försök igen", onAction = onRefresh)
                sections.isEmpty && query.isNotBlank() -> EmptyState("Inga träffar för \"${query.trim()}\".")
                sections.isEmpty -> EmptyState("Inga bevakningar än.", actionLabel = "Ny bevakning", onAction = onAddWatch)
                else -> WatchList(sections, onRowClick, onDelete)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Sök bland bevakningar") },
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
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (sections.triggered.isNotEmpty()) {
            item(key = "h-triggered") { SectionLabel("Utlösta", count = sections.triggered.size) }
            items(sections.triggered, key = { it.id }) { row ->
                SwipeableRow(row, showDivider = row != sections.triggered.first(), onRowClick, onDelete)
            }
        }
        if (sections.waiting.isNotEmpty()) {
            item(key = "h-waiting") { SectionLabel("Väntar", count = sections.waiting.size) }
            items(sections.waiting, key = { it.id }) { row ->
                SwipeableRow(row, showDivider = row != sections.waiting.first(), onRowClick, onDelete)
            }
        }
        item(key = "end") { Box(Modifier.height(Space.xxl)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    row: WatchRowModel,
    showDivider: Boolean,
    onRowClick: (WatchRowModel) -> Unit,
    onDelete: (WatchRowModel) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete(row)
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = Space.screenH),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Ta bort", tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
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
            )
        }
    }
}
