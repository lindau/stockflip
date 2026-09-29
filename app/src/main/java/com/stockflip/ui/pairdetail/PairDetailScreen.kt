package com.stockflip.ui.pairdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.CurrencyHelper
import com.stockflip.PairChartData
import com.stockflip.PairDetailData
import com.stockflip.StockSummary
import com.stockflip.ui.components.CompanyLogoAvatar
import com.stockflip.ui.components.KeyValueRow
import com.stockflip.ui.components.PairPerformanceChart
import com.stockflip.ui.components.PillStatus
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NumericSecondaryStyle
import com.stockflip.ui.theme.NumericStyle
import com.stockflip.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pardetalj i det platta språket: titel + status, spreaden som hjälte, de två aktierna som rader,
 * tunn nyckeltalstabell, graf över prisskillnaden och senaste utlösningar. Tillståndslös.
 */
@Composable
internal fun PairDetailScreen(
    data: PairDetailData,
    chartData: PairChartData?,
    selectedPeriod: ChartPeriod,
    history: List<Long>,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = data.toModel() ?: return
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Space.screenH)) {
            Text(model.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PillStatus(model.status.label, Modifier.padding(top = Space.sm), highlighted = model.status == PairStatus.Triggered)
            Text(
                model.spread,
                style = NumericStyle.copy(fontSize = 44.sp, lineHeight = 48.sp),
                modifier = Modifier.padding(top = Space.md),
            )
            Text(model.spreadLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel("Aktier")
        StockRow(data.stockA, showDivider = false)
        StockRow(data.stockB, showDivider = true)

        SectionLabel("Villkor")
        KeyValueRow("Trigger", model.trigger, showDivider = false)
        KeyValueRow("Notis vid lika pris", if (model.notifyWhenEqual) "Ja" else "Nej")
        KeyValueRow("Kvar till trigger", model.remaining)

        SectionLabel("Prisskillnad")
        if (chartData != null) {
            PairPerformanceChart(data = chartData, selectedPeriod = selectedPeriod, onPeriodSelected = onPeriodSelected)
        } else {
            Text("Laddar graf…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.sm))
        }

        SectionLabel("Senaste utlösningar")
        if (history.isEmpty()) {
            Text("Ingen historik än.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.sm))
        } else {
            val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("sv", "SE"))
            history.take(4).forEachIndexed { i, ts -> KeyValueRow("Utlöst", fmt.format(Date(ts)), showDivider = i > 0) }
        }
    }
}

@Composable
private fun StockRow(stock: StockSummary, showDivider: Boolean) {
    val change = stock.dailyChangePercent
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = Space.screenH), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            CompanyLogoAvatar(symbol = stock.symbol, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(stock.companyName ?: stock.symbol, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stock.symbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stock.lastPrice?.let { CurrencyHelper.formatPrice(it, stock.currency ?: "SEK") } ?: "–", style = NumericStyle)
                Text(
                    change?.let { signed(it) } ?: "–",
                    style = NumericSecondaryStyle,
                    color = when {
                        change == null || change == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        change > 0 -> LocalPriceUp.current
                        else -> LocalPriceDown.current
                    },
                )
            }
        }
    }
}

private fun signed(v: Double): String {
    val sign = if (v > 0) "+" else if (v < 0) "−" else ""
    return "$sign${CurrencyHelper.formatDecimal(abs(v))} %"
}
