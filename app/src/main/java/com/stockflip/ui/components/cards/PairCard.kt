package com.stockflip.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.MetricRow

@Composable
fun PairCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val pricePair = item.watchType as? WatchType.PricePair ?: return
    val targetText = buildString {
        val hasEqual = pricePair.notifyWhenEqual
        val hasDiff = pricePair.priceDifference > 0
        
        if (hasEqual) {
            append("=")
        }
        if (hasEqual && hasDiff) {
            append(" & ")
        }
        if (hasDiff) {
            append("∆ ${priceFormat(pricePair.priceDifference)}")
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // First stock
            MetricRow(
                title = "${item.companyName1 ?: item.ticker1} (${item.ticker1})",
                value = "${priceFormat(item.currentPrice1)} SEK"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Second stock
            MetricRow(
                title = "${item.companyName2 ?: item.ticker2} (${item.ticker2})",
                value = "${priceFormat(item.currentPrice2)} SEK"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Target text - aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (targetText.isNotEmpty()) "Mål: $targetText" else "Mål: =",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

