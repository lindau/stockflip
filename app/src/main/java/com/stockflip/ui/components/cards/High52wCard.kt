package com.stockflip.ui.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.components.StockSummaryRow
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.groupShape

@Composable
fun High52wCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    groupPosition: GroupPosition = GroupPosition.ONLY,
    showStatus: Boolean = false,
    showControls: Boolean = false,
    showPrice: Boolean = true,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val athBased = item.watchType as? WatchType.ATHBased ?: return

    val currency = CurrencyHelper.getCurrencyFromSymbol(item.ticker)

    val targetDropText = when (athBased.dropType) {
        WatchType.DropType.PERCENTAGE -> "${priceFormat(athBased.dropValue)}%"
        WatchType.DropType.ABSOLUTE   -> CurrencyHelper.formatPrice(athBased.dropValue, currency)
    }

    val isTriggered = when (athBased.dropType) {
        WatchType.DropType.PERCENTAGE -> item.currentDropPercentage >= athBased.dropValue
        WatchType.DropType.ABSOLUTE   -> item.currentDropAbsolute >= athBased.dropValue
    }

    val cardBorder = LocalCardBorder.current
    val showStockHeader = showControls || groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.FIRST

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer else containerColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "containerColor"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor,
        ),
        shape = groupShape(groupPosition),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = animatedBorderColor,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusStripe(isTriggered = isTriggered)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = if (showStockHeader) 12.dp else 8.dp),
            ) {
                if (showControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.companyName ?: item.ticker ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (item.ticker != null) {
                                Text(
                                    text = item.ticker,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (onToggleActive != null) {
                            Switch(
                                checked = item.isActive,
                                onCheckedChange = { onToggleActive() },
                                modifier = Modifier
                                    .scale(0.7f)
                                    .align(Alignment.Top)
                                    .offset(y = (-12).dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.FIRST) {
                    StockSummaryRow(
                        companyName = item.companyName,
                        ticker = item.ticker,
                        price = item.currentPrice,
                        dailyChangePercent = item.currentDailyChangePercent,
                        currency = currency,
                        showPrice = item.currentPrice > 0,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                Text(
                    text = "Drawdown: ≥ $targetDropText från 52v-topp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTriggered) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TriggerHistoryRow(triggerHistory)
                LastUpdatedRow(item.lastUpdatedAt, item.updateFailed)

                if (item.isTriggered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TriggeredBadge()
                }
            }
        }
    }
}
