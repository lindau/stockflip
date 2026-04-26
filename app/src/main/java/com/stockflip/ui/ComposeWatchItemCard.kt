package com.stockflip.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.ui.components.cards.LocalIsNewTrigger
import com.stockflip.ui.components.cards.LocalNearTriggerLabel
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.NP
import com.stockflip.ui.components.cards.CombinedAlertCard
import com.stockflip.ui.components.cards.DailyMoveCard
import com.stockflip.ui.components.cards.High52wCard
import com.stockflip.ui.components.cards.MetricAlertCard
import com.stockflip.ui.components.cards.PairCard
import com.stockflip.ui.components.cards.PriceRangeCard
import com.stockflip.CurrencyHelper
import com.stockflip.ui.components.cards.PriceTargetCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposeWatchItemCard(
    item: WatchItem,
    live: LiveWatchData = LiveWatchData(),
    groupPosition: GroupPosition = GroupPosition.ONLY,
    priceFormat: (Double) -> String = { value -> CurrencyHelper.formatDecimal(value) },
    onItemClick: () -> Unit = {},
    showStatus: Boolean = false,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    isNew: Boolean = false,
    nearTriggerLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Vertical outer padding: group items connect tightly — only ONLY/FIRST get top spacing,
    // only ONLY/LAST get bottom spacing.
    val paddingTop = if (groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.FIRST) NP.cardOuterV else 0.dp
    val paddingBottom = if (groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.LAST) NP.cardOuterV else 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(
                when {
                    item.isActive -> 1f
                    showControls -> 1f
                    else -> 0.82f
                }
            )
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onLongClick
            )
            .padding(start = NP.cardOuterH, end = NP.cardOuterH, top = paddingTop, bottom = paddingBottom),
    ) {
        CompositionLocalProvider(
            LocalIsNewTrigger provides isNew,
            LocalNearTriggerLabel provides nearTriggerLabel,
        ) {
        when (item.watchType) {
            is com.stockflip.WatchType.PricePair -> {
                PairCard(
                    item = item,
                    live = live,
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
                    live = live,
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
                    live = live,
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
                    live = live,
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
                    live = live,
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
                    live = live,
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
                    live = live,
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
        } // CompositionLocalProvider
    }
}
