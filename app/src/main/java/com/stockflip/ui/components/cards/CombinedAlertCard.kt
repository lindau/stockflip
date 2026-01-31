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
import com.stockflip.AlertExpression
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.MetricRow
import com.stockflip.ui.components.StatusStripe

@Composable
fun CombinedAlertCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val combined = item.watchType as? WatchType.Combined ?: return
    
    // Get symbols from expression
    val symbols = combined.expression.getSymbols()
    val firstSymbol = symbols.firstOrNull() ?: item.ticker ?: "N/A"
    
    // Get description of expression
    val expressionDescription = combined.expression.getDescription()
    
    // For now, we can't easily determine if combined alert is triggered without evaluating
    // This would require fetching all market data, which is expensive
    // So we'll show it as not triggered by default
    val isTriggered = false

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
                    .padding(12.dp)
            ) {
                // Stock info - visa aktienamn endast en gång
                val symbolText = if (symbols.size == 1) {
                    "${item.companyName ?: firstSymbol} ($firstSymbol)"
                } else {
                    // För flera aktier, visa bara första aktienamnet
                    "${item.companyName ?: firstSymbol} ($firstSymbol)"
                }
                
                MetricRow(
                    title = symbolText,
                    value = if (item.currentPrice > 0) {
                        val currency = com.stockflip.CurrencyHelper.getCurrencyFromSymbol(firstSymbol)
                        com.stockflip.CurrencyHelper.formatPrice(item.currentPrice, currency)
                    } else {
                        "Laddar..."
                    }
                )
            }
        }
    }
}

