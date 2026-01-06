package com.stockflip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.ui.components.cards.High52wCard
import com.stockflip.ui.components.cards.MetricAlertCard
import com.stockflip.ui.components.cards.PairCard
import com.stockflip.ui.components.cards.PriceTargetCard
import com.stockflip.ui.components.cards.PriceRangeCard
import com.stockflip.ui.components.cards.DailyMoveCard
import com.stockflip.ui.components.cards.CombinedAlertCard
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun ComposeWatchItemCard(
    item: WatchItem,
    priceFormat: (Double) -> String = { value ->
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE"))).format(value)
    },
    onItemClick: () -> Unit = {},
    showStatus: Boolean = false,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (item.watchType) {
            is com.stockflip.WatchType.PricePair -> {
                PairCard(
                    item = item,
                    priceFormat = priceFormat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.KeyMetrics -> {
                MetricAlertCard(
                    item = item,
                    priceFormat = priceFormat,
                    showStatus = showStatus,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.PriceTarget -> {
                PriceTargetCard(
                    item = item,
                    priceFormat = priceFormat,
                    showStatus = showStatus,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.PriceRange -> {
                PriceRangeCard(
                    item = item,
                    priceFormat = priceFormat,
                    showStatus = showStatus,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.DailyMove -> {
                DailyMoveCard(
                    item = item,
                    priceFormat = priceFormat,
                    showStatus = showStatus,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.ATHBased -> {
                High52wCard(
                    item = item,
                    priceFormat = priceFormat,
                    showStatus = showStatus,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is com.stockflip.WatchType.Combined -> {
                CombinedAlertCard(
                    item = item,
                    priceFormat = priceFormat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

