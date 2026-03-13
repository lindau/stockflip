package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.stockflip.ui.theme.LocalTextTertiary
import com.stockflip.ui.theme.LocalTrendDown
import com.stockflip.ui.theme.LocalTrendUp
import com.stockflip.ui.theme.NordikNumericSecondaryStyle
import com.stockflip.ui.theme.NordikNumericStyle
import com.stockflip.ui.theme.groupShape

@Composable
fun MetricAlertCard(
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
    val keyMetrics = item.watchType as? WatchType.KeyMetrics ?: return

    val metricTypeName = when (keyMetrics.metricType) {
        WatchType.MetricType.PE_RATIO       -> "P/E"
        WatchType.MetricType.PS_RATIO       -> "P/S"
        WatchType.MetricType.DIVIDEND_YIELD -> "Direktavkastning"
    }

    val directionText = when (keyMetrics.direction) {
        WatchType.PriceDirection.ABOVE -> "Över"
        WatchType.PriceDirection.BELOW -> "Under"
    }

    val targetValueText = when (keyMetrics.metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(keyMetrics.targetValue)}%"
        else                                -> priceFormat(keyMetrics.targetValue)
    }

    val currentValueText = when (keyMetrics.metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(item.currentMetricValue)}%"
        else                                -> priceFormat(item.currentMetricValue)
    }

    val isTriggered = item.currentMetricValue != 0.0 && when (keyMetrics.direction) {
        WatchType.PriceDirection.ABOVE -> item.currentMetricValue >= keyMetrics.targetValue
        WatchType.PriceDirection.BELOW -> item.currentMetricValue <= keyMetrics.targetValue
    }

    val hasValueAtCreation = item.metricValueAtCreation > 0.0
    val trendDirection = if (hasValueAtCreation && item.currentMetricValue > 0.0) {
        when {
            item.currentMetricValue > item.metricValueAtCreation -> "UP"
            item.currentMetricValue < item.metricValueAtCreation -> "DOWN"
            else                                                 -> "SAME"
        }
    } else null

    val trendChange = if (hasValueAtCreation && item.currentMetricValue > 0.0) {
        val change = item.currentMetricValue - item.metricValueAtCreation
        val changePercent = (change / item.metricValueAtCreation) * 100
        Pair(change, changePercent)
    } else null

    val trendUp = LocalTrendUp.current
    val trendDown = LocalTrendDown.current
    val cardBorder = LocalCardBorder.current
    val currency = CurrencyHelper.getCurrencyFromSymbol(item.ticker)
    val showStockHeader = showControls || groupPosition == GroupPosition.ONLY || groupPosition == GroupPosition.FIRST

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer else containerColor,
        ),
        shape = groupShape(groupPosition),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
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

                // Metric data row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$metricTypeName  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = currentValueText,
                            style = NordikNumericStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        trendDirection?.let { direction ->
                            val trendColor = when (direction) {
                                "UP"   -> trendUp
                                "DOWN" -> trendDown
                                else   -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when (direction) {
                                    "UP"  -> Icons.Default.ArrowUpward
                                    else  -> Icons.Default.ArrowDownward
                                },
                                contentDescription = null,
                                tint = trendColor,
                                modifier = Modifier.size(12.dp),
                            )
                            trendChange?.let { (change, changePercent) ->
                                Text(
                                    text = "${if (change >= 0) "+" else ""}${priceFormat(changePercent)}%",
                                    style = NordikNumericSecondaryStyle,
                                    color = trendColor,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Mål: $directionText $targetValueText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTriggered) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (hasValueAtCreation) {
                    val valueAtCreationText = when (keyMetrics.metricType) {
                        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(item.metricValueAtCreation)}%"
                        else                                -> priceFormat(item.metricValueAtCreation)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vid skapande: $valueAtCreationText",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalTextTertiary.current,
                    )
                }

                TriggerHistoryRow(triggerHistory)

                if (item.isTriggered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TriggeredBadge()
                }
            }
        }
    }
}
