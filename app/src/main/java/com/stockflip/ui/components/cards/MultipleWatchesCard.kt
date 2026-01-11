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
import com.stockflip.ui.components.MetricRow

/**
 * Card för att visa att en aktie har flera bevakningar.
 * När användaren klickar på detta kort ska de navigera till StockDetailFragment.
 */
@Composable
fun MultipleWatchesCard(
    symbol: String,
    companyName: String?,
    watchCount: Int,
    currentPrice: Double,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
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
                .padding(12.dp)
        ) {
            // Stock info
            MetricRow(
                title = "${companyName ?: symbol} ($symbol)",
                value = "${priceFormat(currentPrice)} SEK"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Multiple watches text - aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "$watchCount bevakningar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

