package com.stockflip.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe

@Composable
fun MetricAlertCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
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

    Card(
        modifier = modifier.fillMaxWidth(),
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
                // Metric row
                Column {
                    Text(
                        text = "${item.companyName ?: item.ticker} (${item.ticker})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$metricTypeName: $currentValueText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Target text
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
            }
        }
    }
}

