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
import com.stockflip.CurrencyHelper
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.MetricRow
import kotlin.math.abs

@Composable
fun PairCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val pricePair = item.watchType as? WatchType.PricePair ?: return

    // Hämta valuta för varje aktie
    val currency1 = CurrencyHelper.getCurrencyFromSymbol(item.ticker1)
    val currency2 = CurrencyHelper.getCurrencyFromSymbol(item.ticker2)

    val isTriggered = item.currentPrice1 > 0.0 && item.currentPrice2 > 0.0 && run {
        val diff = abs(item.currentPrice1 - item.currentPrice2)
        (pricePair.notifyWhenEqual && diff < 0.01) ||
            (pricePair.priceDifference > 0 && diff <= pricePair.priceDifference)
    }

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
            containerColor = if (isTriggered)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // First stock
            MetricRow(
                title = "${item.companyName1 ?: item.ticker1} (${item.ticker1})",
                value = CurrencyHelper.formatPrice(item.currentPrice1, currency1)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Second stock
            MetricRow(
                title = "${item.companyName2 ?: item.ticker2} (${item.ticker2})",
                value = CurrencyHelper.formatPrice(item.currentPrice2, currency2)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Target text - aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (targetText.isNotEmpty()) "Mål: $targetText" else "Mål: =",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTriggered)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
