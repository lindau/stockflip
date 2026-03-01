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
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.components.StockSummaryRow

@Composable
fun DailyMoveCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    showStatus: Boolean = false,
    showControls: Boolean = false,
    showPrice: Boolean = true,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dailyMove = item.watchType as? WatchType.DailyMove ?: return
    
    val directionText = when (dailyMove.direction) {
        WatchType.DailyMoveDirection.UP -> "upp"
        WatchType.DailyMoveDirection.DOWN -> "ned"
        WatchType.DailyMoveDirection.BOTH -> "båda"
    }
    
    // Note: DailyMove kan inte highlightas baserat på currentPrice, behöver dailyChangePercent
    // För nu antar vi att det inte är triggat om vi inte har dailyChangePercent
    val isTriggered = false

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
                val currency = CurrencyHelper.getCurrencyFromSymbol(item.ticker)
                StockSummaryRow(
                    companyName = item.companyName,
                    ticker = item.ticker,
                    price = item.currentPrice,
                    dailyChangePercent = item.currentDailyChangePercent,
                    currency = currency,
                    showPrice = showPrice
                )
                if (showControls && onToggleActive != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (showPrice) 8.dp else 4.dp))
                // Target text - aligned to the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Mål: Dagsrörelse ≥ ${priceFormat(dailyMove.percentThreshold)}% ($directionText)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                
            }
        }
    }
}

