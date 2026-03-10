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
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.R
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NordikNumericSecondaryStyle
import com.stockflip.ui.theme.NordikNumericStyle
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Summaryrad för en aktie: namn + ticker till vänster, pris + daglig förändring till höger.
 *
 * Typografisk hierarki:
 * - Bolagsnamn: [titleMedium] (SemiBold 15sp) — primär identifierare
 * - Ticker: [labelMedium] (Medium 11sp, 0.6sp spacing) — sekundär kod
 * - Pris: [NordikNumericStyle] (SemiBold 15sp, tnum) — primärdata
 * - Daglig förändring: [NordikNumericSecondaryStyle] (Normal 12sp, tnum) — sekundärdata
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
    action: (@Composable () -> Unit)? = null,
) {
    val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = companyName ?: ticker ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (ticker != null) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showPrice) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = if (price > 0) CurrencyHelper.formatPrice(price, currency) else "—",
                    style = NordikNumericStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (dailyChangePercent != null) {
                    val isPositive = dailyChangePercent >= 0
                    val trendColor = if (isPositive) LocalPriceUp.current else LocalPriceDown.current
                    val arrowRes = if (isPositive) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Icon(
                            painter = painterResource(id = arrowRes),
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${if (dailyChangePercent >= 0) "+" else ""}${priceFormat.format(dailyChangePercent)}%",
                            style = NordikNumericSecondaryStyle,
                            color = trendColor,
                        )
                    }
                }
            }
        }
        action?.invoke()
    }
}
