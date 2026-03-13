package com.stockflip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.NP
import com.stockflip.ui.components.cards.CombinedAlertCard
import com.stockflip.ui.components.cards.DailyMoveCard
import com.stockflip.ui.components.cards.High52wCard
import com.stockflip.ui.components.cards.MetricAlertCard
import com.stockflip.ui.components.cards.PairCard
import com.stockflip.ui.components.cards.PriceRangeCard
import com.stockflip.ui.components.cards.PriceTargetCard
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun ComposeWatchItemCard(
    item: WatchItem,
    groupPosition: GroupPosition = GroupPosition.ONLY,
    priceFormat: (Double) -> String = { value ->
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE"))).format(value)
    },
    onItemClick: () -> Unit = {},
    showStatus: Boolean = false,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // Vertical outer padding: group items connect tightly — only ONLY/FIRST get top spacing,
    // only ONLY/LAST get bottom spacing.
    val paddingTop = if (groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.FIRST) NP.cardOuterV else 0.dp
    val paddingBottom = if (groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.LAST) NP.cardOuterV else 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (item.isActive) 1f else 0.45f)
            .clickable(onClick = onItemClick)
            .padding(start = NP.cardOuterH, end = NP.cardOuterH, top = paddingTop, bottom = paddingBottom),
    ) {
        when (item.watchType) {
            is com.stockflip.WatchType.PricePair -> {
                PairCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.KeyMetrics -> {
                MetricAlertCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showStatus = showStatus,
                    showControls = showControls,
                    showPrice = !showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.PriceTarget -> {
                PriceTargetCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showStatus = showStatus,
                    showControls = showControls,
                    showPrice = !showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.PriceRange -> {
                PriceRangeCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showStatus = showStatus,
                    showControls = showControls,
                    showPrice = !showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.DailyMove -> {
                DailyMoveCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showStatus = showStatus,
                    showControls = showControls,
                    showPrice = !showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.ATHBased -> {
                High52wCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showStatus = showStatus,
                    showControls = showControls,
                    showPrice = !showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is com.stockflip.WatchType.Combined -> {
                CombinedAlertCard(
                    item = item,
                    priceFormat = priceFormat,
                    groupPosition = groupPosition,
                    showPrice = !showControls,
                    showControls = showControls,
                    onToggleActive = onToggleActive,
                    onClick = if (showControls) onItemClick else null,
                    containerColor = containerColor,
                    triggerHistory = triggerHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
