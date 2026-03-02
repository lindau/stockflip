package com.stockflip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.R
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Summary row for a stock: name, ticker on the left; price and daily % change with trend icon on the right.
 * Matches the layout shown in the Aktier tab screenshot.
 */
@Composable
fun StockSummaryRow(
    companyName: String?,
    ticker: String?,
    price: Double,
    dailyChangePercent: Double?,
    currency: String,
    showPrice: Boolean = true,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = companyName ?: ticker ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (ticker != null) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showPrice) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = if (price > 0) CurrencyHelper.formatPrice(price, currency) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (dailyChangePercent != null) {
                    val isPositive = dailyChangePercent >= 0
                    val trendColor = if (isPositive) LocalPriceUp.current else LocalPriceDown.current
                    val arrowRes = if (isPositive) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            painter = painterResource(id = arrowRes),
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${if (dailyChangePercent >= 0) "+" else ""}${priceFormat.format(dailyChangePercent)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = trendColor
                        )
                    }
                }
            }
        }
        action?.invoke()
    }
}
