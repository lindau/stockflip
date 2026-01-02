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
import com.stockflip.ui.components.MetricRow
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.components.TonalTagChip

@Composable
fun PriceRangeCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val priceRange = item.watchType as? WatchType.PriceRange ?: return
    
    val isTriggered = item.currentPrice >= priceRange.minPrice && item.currentPrice <= priceRange.maxPrice

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricRow(
                        title = "${item.companyName ?: item.ticker} (${item.ticker})",
                        value = "${priceFormat(item.currentPrice)} SEK"
                    )
                    TonalTagChip(text = "Prisintervall")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Mål: Pris mellan ${priceFormat(priceRange.minPrice)} - ${priceFormat(priceRange.maxPrice)} SEK",
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

