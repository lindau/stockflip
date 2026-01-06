package com.stockflip.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe

@Composable
fun MetricAlertCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    showStatus: Boolean = false,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val keyMetrics = item.watchType as? WatchType.KeyMetrics ?: return
    
    val metricTypeName = when (keyMetrics.metricType) {
        WatchType.MetricType.PE_RATIO -> "P/E-tal"
        WatchType.MetricType.PS_RATIO -> "P/S-tal"
        WatchType.MetricType.DIVIDEND_YIELD -> "Direktavkastning"
    }
    
    val directionText = when (keyMetrics.direction) {
        WatchType.PriceDirection.ABOVE -> "Över"
        WatchType.PriceDirection.BELOW -> "Under"
    }
    
    val targetValueText = when (keyMetrics.metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(keyMetrics.targetValue)}%"
        else -> priceFormat(keyMetrics.targetValue)
    }
    
    val currentValueText = when (keyMetrics.metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(item.currentMetricValue)}%"
        else -> priceFormat(item.currentMetricValue)
    }
    
    val isTriggered = item.currentMetricValue != 0.0 && when (keyMetrics.direction) {
        WatchType.PriceDirection.ABOVE -> item.currentMetricValue >= keyMetrics.targetValue
        WatchType.PriceDirection.BELOW -> item.currentMetricValue <= keyMetrics.targetValue
    }
    
    // Beräkna trend jämfört med värde vid skapande
    val hasValueAtCreation = item.metricValueAtCreation > 0.0
    val trendDirection = if (hasValueAtCreation && item.currentMetricValue > 0.0) {
        when {
            item.currentMetricValue > item.metricValueAtCreation -> "UP"
            item.currentMetricValue < item.metricValueAtCreation -> "DOWN"
            else -> "SAME"
        }
    } else null
    
    val trendChange = if (hasValueAtCreation && item.currentMetricValue > 0.0) {
        val change = item.currentMetricValue - item.metricValueAtCreation
        val changePercent = (change / item.metricValueAtCreation) * 100
        Pair(change, changePercent)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusStripe(isTriggered = isTriggered)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header row med stock name och switch i övre högra hörnet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Stock name
                    Text(
                        text = "${item.companyName ?: item.ticker} (${item.ticker})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Toggle switch - övre högra hörnet
                    if (showControls && onToggleActive != null) {
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier = Modifier.scale(0.7f) // Gör switchen mindre
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Metric row
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$metricTypeName: $currentValueText",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Trend-indikator
                        trendDirection?.let { direction ->
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Icon(
                                imageVector = when (direction) {
                                    "UP" -> Icons.Default.ArrowUpward
                                    "DOWN" -> Icons.Default.ArrowDownward
                                    else -> Icons.Default.ArrowUpward
                                },
                                contentDescription = null,
                                tint = when (direction) {
                                    "UP" -> Color(0xFF4CAF50) // Green
                                    "DOWN" -> Color(0xFFF44336) // Red
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            trendChange?.let { (change, changePercent) ->
                                Text(
                                    text = "${if (change >= 0) "+" else ""}${priceFormat(changePercent)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (direction) {
                                        "UP" -> Color(0xFF4CAF50)
                                        "DOWN" -> Color(0xFFF44336)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    // Visa värde vid skapande om det finns
                    if (hasValueAtCreation) {
                        val valueAtCreationText = when (keyMetrics.metricType) {
                            WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(item.metricValueAtCreation)}%"
                            else -> priceFormat(item.metricValueAtCreation)
                        }
                        Text(
                            text = "Vid skapande: $valueAtCreationText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Target text - aligned to the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Mål: $directionText $targetValueText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isTriggered) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                // Status text - visas när showStatus är true
                if (showStatus) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = com.stockflip.ui.components.cards.formatAlertStatus(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
            }
        }
    }
}

