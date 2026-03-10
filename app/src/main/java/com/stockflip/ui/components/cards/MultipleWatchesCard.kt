package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
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
import com.stockflip.ui.components.StockSummaryRow
import com.stockflip.ui.theme.ListCardShape
import com.stockflip.ui.theme.LocalCardBorder

/**
 * Kort som visar en aktie med flera bevakningar — används på Aktier-tabben.
 * Klick navigerar till StockDetailFragment.
 *
 * Triggered state baseras på [triggeredCount] > 0. Container-ton och räknare
 * använder tertiary (amber) — semantiskt korrekt, inte alarmistisk.
 */
@Composable
fun MultipleWatchesCard(
    symbol: String,
    companyName: String?,
    watchCount: Int,
    triggeredCount: Int = 0,
    currentPrice: Double,
    dailyChangePercent: Double? = null,
    priceFormat: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val isTriggered = triggeredCount > 0
    val cardBorder = LocalCardBorder.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
        shape = ListCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            val currency = CurrencyHelper.getCurrencyFromSymbol(symbol)
            StockSummaryRow(
                companyName = companyName,
                ticker = symbol,
                price = currentPrice,
                dailyChangePercent = dailyChangePercent,
                currency = currency,
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "$watchCount bevakningar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (triggeredCount > 0) {
                    Text(
                        text = " · $triggeredCount nådd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
