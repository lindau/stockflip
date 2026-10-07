package com.stockflip.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stockflip.MarketMover
import com.stockflip.MoverList
import com.stockflip.MoverMarket
import com.stockflip.R
import com.stockflip.UiState
import com.stockflip.ui.components.SkeletonRow
import com.stockflip.ui.components.StockSummaryRow
import com.stockflip.ui.theme.Space

/** Tillstånd och händelser för "Heta just nu" på Marknad-fliken. */
internal data class MoversUi(
    val market: MoverMarket,
    val list: MoverList,
    val state: UiState<List<MarketMover>>,
    val onMarketChange: (MoverMarket) -> Unit,
    val onListChange: (MoverList) -> Unit,
    val onOpen: (MarketMover) -> Unit,
    val onRetry: () -> Unit,
)

internal fun MoverMarket.labelRes(): Int = when (this) {
    MoverMarket.SWEDEN -> R.string.market_movers_sverige
    MoverMarket.US -> R.string.market_movers_usa
}

internal fun MoverList.labelRes(): Int = when (this) {
    MoverList.GAINERS -> R.string.market_movers_uppgang
    MoverList.LOSERS -> R.string.market_movers_nedgang
    MoverList.MOST_ACTIVE -> R.string.market_movers_omsatta
    MoverList.TRENDING -> R.string.market_movers_trendar
}

/** Väljare (marknad + lista) och själva raderna. Rubriken "Heta just nu" ritas av anroparen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoversContent(ui: MoversUi) {
    Column(Modifier.fillMaxWidth()) {
        SegmentedChoice(MoverMarket.entries, ui.market, { stringResource(it.labelRes()) }, ui.onMarketChange)
        ChipChoice(MoverList.entries.filter { it.isAvailableFor(ui.market) }, ui.list, { stringResource(it.labelRes()) }, ui.onListChange)
        when (val state = ui.state) {
            UiState.Loading -> Column { repeat(5) { SkeletonRow() } }
            is UiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.sm),
                )
                TextButton(onClick = ui.onRetry, modifier = Modifier.padding(horizontal = Space.sm)) {
                    Text(stringResource(R.string.market_forsok_igen))
                }
            }
            is UiState.Success -> if (state.data.isEmpty()) {
                Text(
                    stringResource(R.string.market_movers_tom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.sm),
                )
            } else {
                state.data.forEachIndexed { index, mover ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Space.screenH),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    StockSummaryRow(
                        companyName = mover.name,
                        ticker = mover.symbol,
                        price = mover.price,
                        dailyChangePercent = mover.changePercent,
                        currency = mover.currency,
                        modifier = Modifier
                            .clickable { ui.onOpen(mover) }
                            .padding(horizontal = Space.screenH, vertical = Space.md),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.sm)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

/** Alternativ i en scrollbar rad: hamnar alltid på en rad, även med fyra val eller stor systemtext. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.screenH, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1, softWrap = false) },
            )
        }
    }
}
