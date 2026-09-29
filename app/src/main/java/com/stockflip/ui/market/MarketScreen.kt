package com.stockflip.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import com.stockflip.ui.components.CompanyLogoAvatar
import com.stockflip.ui.components.EmptyState
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.SkeletonRow
import com.stockflip.ui.theme.Space

/** Vad som visas under sökfältet, härlett ur söktext, söktillstånd och historik. */
internal sealed interface MarketContent {
    data object Hint : MarketContent
    data object Loading : MarketContent
    data class Results(val items: List<StockSearchResult>) : MarketContent
    data object NoResults : MarketContent
    data class Failed(val message: String) : MarketContent
    data class Recent(val items: List<StockSearchResult>) : MarketContent
}

/** Ren beslutslogik för Marknad-fliken (testbar utan Compose). */
internal fun marketContentFor(
    query: String,
    state: SearchState,
    recent: List<StockSearchResult>,
): MarketContent {
    val q = query.trim()
    return when {
        q.length < 2 -> if (recent.isEmpty()) MarketContent.Hint else MarketContent.Recent(recent)
        state is SearchState.Loading -> MarketContent.Loading
        state is SearchState.Error -> MarketContent.Failed(state.message)
        state is SearchState.Success && state.results.isEmpty() -> MarketContent.NoResults
        state is SearchState.Success -> MarketContent.Results(state.results)
        else -> MarketContent.Hint
    }
}

/** Marknad: sök en aktie, ett index eller en kryptovaluta och öppna dess detaljvy. Tillståndslös. */
@Composable
internal fun MarketScreen(
    query: String,
    content: MarketContent,
    onQueryChange: (String) -> Unit,
    onResultClick: (StockSearchResult) -> Unit,
    onRetry: () -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            "Marknad",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Space.screenH, end = Space.screenH, top = Space.md),
        )
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Sök aktie, index eller krypto") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Rensa sökning")
                    }
                }
            },
            shape = MaterialTheme.shapes.small,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.sm),
        )
        when (content) {
            MarketContent.Hint -> EmptyState("Sök på namn eller ticker, t.ex. Volvo, AAPL eller BTC.")
            MarketContent.Loading -> Column { repeat(5) { SkeletonRow() } }
            MarketContent.NoResults -> EmptyState("Inga träffar för \"${query.trim()}\".")
            is MarketContent.Failed -> EmptyState(content.message, actionLabel = "Försök igen", onAction = onRetry)
            is MarketContent.Results -> ResultList(content.items, header = null, onResultClick, onAction = null)
            is MarketContent.Recent -> ResultList(content.items, header = "Senast sökta", onResultClick, onClearRecent)
        }
    }
}

@Composable
private fun ResultList(
    items: List<StockSearchResult>,
    header: String?,
    onClick: (StockSearchResult) -> Unit,
    onAction: (() -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (header != null) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(header)
                    if (onAction != null) {
                        TextButton(onClick = onAction) { Text("Rensa") }
                    }
                }
            }
        }
        itemsIndexed(items, key = { _, it -> it.symbol }) { index, result ->
            ResultRow(result, showDivider = index > 0, onClick = { onClick(result) })
        }
    }
}

@Composable
private fun ResultRow(result: StockSearchResult, showDivider: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Space.screenH),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clickable(onClick = onClick)
                .padding(horizontal = Space.screenH, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            CompanyLogoAvatar(symbol = result.symbol, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    result.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result.symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
