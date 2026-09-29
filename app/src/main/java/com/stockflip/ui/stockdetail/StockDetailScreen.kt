package com.stockflip.ui.stockdetail

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import com.stockflip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.supportsIndicators
import com.stockflip.BollingerPoint
import com.stockflip.ChartIndicatorConfig
import com.stockflip.ChartPeriod
import com.stockflip.IntradayChartData
import com.stockflip.RsiPoint
import com.stockflip.SmaChartLevel
import com.stockflip.StockDetailData
import com.stockflip.WatchItemUiState
import com.stockflip.isTriggeredForDisplay
import com.stockflip.ui.components.KeyValueRow
import com.stockflip.ui.components.PillStatus
import com.stockflip.ui.components.PriceText
import com.stockflip.ui.components.RangeBar
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.SegmentedControl
import com.stockflip.ui.components.cards.ChartSettingsDialog
import com.stockflip.ui.components.cards.ClarityChartWithIndicators
import com.stockflip.ui.components.cards.calculatePeriodChange
import com.stockflip.ui.components.cards.earningsLabel
import com.stockflip.ui.components.formatNumber
import com.stockflip.ui.components.formatSignedPercent
import com.stockflip.ui.components.rangeFraction
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NordikNumericStyle
import com.stockflip.ui.theme.Space
import com.stockflip.ui.watchlist.toRowModel

/**
 * Aktiedetalj: hero (kurs + förändring), ren graf med streckade bevakningsnivåer,
 * periodval, tunn nyckeltalstabell och "Dina bevakningar". Tillståndslös.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockDetailScreen(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
    alerts: List<WatchItemUiState>,
    isRefreshing: Boolean,
    smaLevels: List<SmaChartLevel>,
    bollingerPoints: List<BollingerPoint>,
    rsiPoints: List<RsiPoint>,
    indicatorConfig: ChartIndicatorConfig,
    onIndicatorConfigChange: (ChartIndicatorConfig) -> Unit,
    onPeriodSelected: (ChartPeriod) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onAddWatch: () -> Unit,
    onEditAlert: (WatchItemUiState) -> Unit,
    onAlertAction: (WatchItemUiState, AlertAction) -> Unit,
    note: String?,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIndicators by remember { mutableStateOf(false) }
    val periods = ChartPeriod.entries
    val change = calculatePeriodChange(data, chartData, selectedPeriod)
    val changePercent = change.percent
    val changeColor = when {
        changePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        changePercent >= 0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val levels = watchLevelsFor(alerts, data)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.stockdetail_tillbaka)) }
                IconButton(onClick = { showIndicators = true }) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.stockdetail_indikatorer)) }
            }
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "hero") {
                        Column(Modifier.padding(horizontal = Space.screenH)) {
                            Text(data.companyName, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(data.symbol, data.exchange, data.currency).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.padding(top = Space.md),
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                PriceText(
                                    text = data.lastPrice?.let { formatNumber(it) } ?: "–",
                                    value = data.lastPrice ?: 0.0,
                                    style = NordikNumericStyle.copy(fontSize = 44.sp, lineHeight = 48.sp),
                                )
                                if (changePercent != null) {
                                    Text(
                                        "${formatSignedPercent(changePercent)} ${periodWord(selectedPeriod)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = changeColor,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "chart") {
                        ClarityChartWithIndicators(
                            chartData = chartData,
                            isPositive = changePercent == null || changePercent >= 0,
                            lineColor = changeColor,
                            selectedPeriod = selectedPeriod,
                            smaLevels = smaLevels,
                            indicatorConfig = indicatorConfig,
                            bollingerPoints = bollingerPoints,
                            rsiPoints = rsiPoints,
                            chartHeight = 200.dp,
                            watchLevels = levels.map { it.price },
                            modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.md).semantics {
                                contentDescription = "Kursgraf, ${periodWord(selectedPeriod)}" +
                                    (changePercent?.let { ", ${formatSignedPercent(it)}" } ?: "")
                            },
                        )
                    }
                    item(key = "periods") {
                        SegmentedControl(
                            options = periods.map { it.label },
                            selectedIndex = periods.indexOf(selectedPeriod),
                            onSelect = { onPeriodSelected(periods[it]) },
                            modifier = Modifier.padding(horizontal = Space.screenH),
                        )
                    }
                    item(key = "figures") {
                        Column(Modifier.padding(top = Space.lg)) {
                            SectionLabel(stringResource(R.string.stockdetail_nyckeltal))
                            val low = data.week52Low
                            val high = data.week52High
                            val price = data.lastPrice
                            if (low != null && high != null && price != null && high > low) {
                                RangeBar("52 veckor", formatNumber(low), formatNumber(high), rangeFraction(price, low, high))
                            }
                            val figures = keyFiguresFor(data) +
                                listOfNotNull(data.nextEarnings?.let { earningsLabel(it.reportDateMillis, it.isAnnualReport, System.currentTimeMillis()) }
                                    ?.let { KeyFigure("Nästa rapport", it) })
                            figures.forEachIndexed { i, f -> KeyValueRow(f.label, f.value, showDivider = i > 0 || price != null) }
                        }
                    }
                    item(key = "alerts-h") {
                        Box(Modifier.padding(top = Space.lg)) { SectionLabel(stringResource(R.string.stockdetail_dina_bevakningar), count = alerts.size.takeIf { it > 0 }) }
                    }
                    if (alerts.isEmpty()) {
                        item(key = "alerts-empty") {
                            Text(
                                stringResource(R.string.stockdetail_inga_bevakningar_pa_den_har_aktien_an),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Space.screenH, vertical = Space.sm),
                            )
                        }
                    } else {
                        items(alerts, key = { it.item.id }) { alert ->
                            val row = alert.toRowModel()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable { onEditAlert(alert) }
                                    .padding(horizontal = Space.screenH, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.subtitle.substringBefore(" · utlöst").substringBefore(" · pausad"),
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                val triggered = alert.isTriggeredForDisplay()
                                PillStatus(
                                    text = alertStatusLabel(triggered, alert.item.isActive),
                                    highlighted = triggered,
                                )
                                val action = alertActionFor(triggered, alert.item.isActive)
                                TextButton(onClick = { onAlertAction(alert, action) }) { Text(action.label) }
                            }
                        }
                    }
                    item(key = "note") {
                        Column(Modifier.padding(top = Space.lg)) {
                            SectionLabel(stringResource(R.string.stockdetail_anteckning))
                            Text(
                                note?.takeIf { it.isNotBlank() } ?: "Lägg till en anteckning",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditNote)
                                    .padding(horizontal = Space.screenH, vertical = Space.md),
                            )
                        }
                    }
                    item(key = "end") { Box(Modifier.height(96.dp)) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddWatch,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.stockdetail_ny_bevakning)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.md),
        )
    }
    if (showIndicators) {
        ChartSettingsDialog(
            config = indicatorConfig,
            canShowIndicators = selectedPeriod.supportsIndicators(),
            onChange = onIndicatorConfigChange,
            onDismiss = { showIndicators = false },
        )
    }
}

private fun periodWord(period: ChartPeriod): String = when (period) {
    ChartPeriod.DAY -> "idag"
    ChartPeriod.WEEK -> "1 v"
    ChartPeriod.MONTH -> "1 mån"
    ChartPeriod.THREE_MONTHS -> "3 mån"
    ChartPeriod.SIX_MONTHS -> "6 mån"
    ChartPeriod.YEAR -> "1 år"
    ChartPeriod.FIVE_YEARS -> "5 år"
}
