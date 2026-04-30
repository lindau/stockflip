package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.PairChartData
import com.stockflip.PairDetailData
import com.stockflip.StockSummary
import com.stockflip.WatchType
import com.stockflip.ui.components.PairPerformanceChart
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.LocalTextTertiary
import com.stockflip.ui.theme.NordikNumericStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ClarityPairDetailPanel(
    data: PairDetailData,
    chartData: PairChartData?,
    selectedPeriod: ChartPeriod,
    history: List<Long>,
    onPeriodSelected: (ChartPeriod) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClarityPairHeroCard(data = data)
        if (chartData != null) {
            ClarityPairChartCard(
                data = chartData,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
            )
        } else {
            ClarityPairChartLoadingCard()
        }
        ClarityPairActionCard(
            data = data,
            history = history,
            onEdit = onEdit,
            onToggleActive = onToggleActive,
        )
    }
}

@Composable
private fun ClarityPairHeroCard(data: PairDetailData) {
    val colorScheme = MaterialTheme.colorScheme
    val pairType = data.watchItem.watchType as? WatchType.PricePair ?: return
    val currentSpread = data.spread?.let { abs(it) }
    val targetSpread = pairType.priceDifference.takeIf { it > 0.0 }
    val isTriggered = data.watchItem.isTriggered ||
        (currentSpread != null && targetSpread != null && currentSpread <= targetSpread) ||
        (pairType.notifyWhenEqual && currentSpread != null && currentSpread < 0.01)
    val signalColor = when {
        isTriggered -> LocalPriceUp.current
        currentSpread == null -> colorScheme.onSurfaceVariant
        targetSpread != null -> LocalPriceDown.current
        else -> colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AKTIEPAR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = LocalTextTertiary.current,
                    )
                    Text(
                        text = pairTitle(data),
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ClarityPairBadge(
                    text = when {
                        data.watchItem.isTriggered -> "Utlöst"
                        data.watchItem.isActive -> "Aktiv"
                        else -> "Inaktiv"
                    },
                    color = if (data.watchItem.isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClarityPairStockCell(
                    stock = data.stockA,
                    modifier = Modifier.weight(1f),
                )
                ClarityPairStockCell(
                    stock = data.stockB,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = currentSpread?.let { CurrencyHelper.formatDecimal(it) } ?: "—",
                        style = NordikNumericStyle.copy(
                            fontSize = 42.sp,
                            lineHeight = 48.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Skillnad nu",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalTextTertiary.current,
                    )
                }
                ClarityPairSignalLine(
                    currentSpread = currentSpread,
                    targetSpread = targetSpread,
                    isTriggered = isTriggered,
                    color = signalColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClarityPairMetricCell(
                    label = "MÅL",
                    value = targetSpread?.let { CurrencyHelper.formatDecimal(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                ClarityPairMetricCell(
                    label = "LIKA PRIS",
                    value = if (pairType.notifyWhenEqual) "Ja" else "Nej",
                    modifier = Modifier.weight(1f),
                )
                ClarityPairMetricCell(
                    label = "KVAR",
                    value = distanceText(currentSpread, targetSpread),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ClarityPairStockCell(
    stock: StockSummary,
    modifier: Modifier = Modifier,
) {
    val change = stock.dailyChangePercent
    val changeColor = when {
        change == null -> MaterialTheme.colorScheme.onSurfaceVariant
        change >= 0.0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val flag = flagForCurrency(stock.currency)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = listOfNotNull(flag, stock.companyName ?: stock.symbol).joinToString(" "),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stock.symbol,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = LocalTextTertiary.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stock.lastPrice?.let { CurrencyHelper.formatPrice(it, stock.currency ?: "SEK") } ?: "—",
            modifier = Modifier.padding(top = 10.dp),
            style = NordikNumericStyle.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = change?.let { signedPercent(it) } ?: "— %",
            modifier = Modifier
                .padding(top = 6.dp)
                .background(changeColor.copy(alpha = 0.13f), RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = changeColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClarityPairChartCard(
    data: PairChartData,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            Text(
                text = "Prisskillnad",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            PairPerformanceChart(
                data = data,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ClarityPairChartLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Laddar graf",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClarityPairActionCard(
    data: PairDetailData,
    history: List<Long>,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Redigera")
                }
                FilledTonalButton(
                    onClick = onToggleActive,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(if (data.watchItem.isActive) "Inaktivera" else "Aktivera")
                }
            }

            Text(
                text = "Senaste triggers",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LocalTextTertiary.current,
            )
            Text(
                text = historyText(history),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ClarityPairBadge(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun ClarityPairMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
            ),
            color = LocalTextTertiary.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 5.dp),
            style = NordikNumericStyle.copy(
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClarityPairSignalLine(
    currentSpread: Double?,
    targetSpread: Double?,
    isTriggered: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val values = when {
        currentSpread == null -> listOf(0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f)
        isTriggered -> listOf(0.70f, 0.63f, 0.58f, 0.50f, 0.42f, 0.34f)
        targetSpread != null -> listOf(0.34f, 0.40f, 0.45f, 0.54f, 0.60f, 0.68f)
        else -> listOf(0.54f, 0.47f, 0.50f, 0.43f, 0.48f, 0.39f)
    }
    Canvas(modifier = modifier) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.coerceAtLeast(1)
            val y = size.height * value
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = Offset(size.width, size.height * values.last()),
        )
    }
}

private fun pairTitle(data: PairDetailData): String {
    val left = data.stockA.companyName ?: data.stockA.symbol
    val right = data.stockB.companyName ?: data.stockB.symbol
    return "$left ÷ $right"
}

private fun distanceText(currentSpread: Double?, targetSpread: Double?): String {
    if (currentSpread == null || targetSpread == null) return "—"
    return CurrencyHelper.formatDecimal((currentSpread - targetSpread).coerceAtLeast(0.0))
}

private fun signedPercent(value: Double): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    return "$sign${CurrencyHelper.formatDecimal(abs(value))} %"
}

private fun flagForCurrency(currency: String?): String? {
    val countryCode = CountryFlagHelper.getCountryCodeFromCurrency(currency) ?: return null
    return CountryFlagHelper.getFlagEmoji(countryCode)
}

private fun historyText(history: List<Long>): String {
    if (history.isEmpty()) return "Ingen historik"
    val formatter = SimpleDateFormat("d MMM yyyy HH:mm", Locale("sv", "SE"))
    return history.take(4).joinToString("\n") { formatter.format(Date(it)) }
}
