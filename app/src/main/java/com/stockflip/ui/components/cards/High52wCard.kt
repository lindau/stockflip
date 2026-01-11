package com.stockflip.ui.components.cards

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe

@Composable
fun High52wCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    showStatus: Boolean = false,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
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
                    .padding(12.dp)
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Target text - aligned to the right
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

