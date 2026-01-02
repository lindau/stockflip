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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe

@Composable
fun High52wCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val athBased = item.watchType as? WatchType.ATHBased ?: return
    
    val currentDropText = when (athBased.dropType) {
        WatchType.DropType.PERCENTAGE -> "${priceFormat(item.currentDropPercentage)}%"
        WatchType.DropType.ABSOLUTE -> "${priceFormat(item.currentDropAbsolute)} SEK"
    }
    
    val targetDropText = when (athBased.dropType) {
        WatchType.DropType.PERCENTAGE -> "${priceFormat(athBased.dropValue)}%"
        WatchType.DropType.ABSOLUTE -> "${priceFormat(athBased.dropValue)} SEK"
    }
    
    val isTriggered = when (athBased.dropType) {
        WatchType.DropType.PERCENTAGE -> item.currentDropPercentage >= athBased.dropValue
        WatchType.DropType.ABSOLUTE -> item.currentDropAbsolute >= athBased.dropValue
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
                // Title
                Text(
                    text = "${item.companyName ?: item.ticker} (${item.ticker})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // 52w high info
                Text(
                    text = "52-veckorshögsta: ${priceFormat(item.currentATH)} | $currentDropText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Target text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Mål: Nedgång $targetDropText",
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

