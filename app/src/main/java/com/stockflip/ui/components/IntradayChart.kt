package com.stockflip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.IntradayChartData
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntradayChart(
    data: IntradayChartData,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChartPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = period == selectedPeriod,
                    onClick = { onPeriodSelected(period) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ChartPeriod.entries.size),
                    label = {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    icon = {}
                )
            }
        }

        if (data.prices.isNotEmpty() && data.emptyReason != null) {
            Text(
                text = "Fallback",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }

        if (data.prices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = data.emptyReason ?: "Marknaden stängd",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    data.lastTradeTimestamp?.let { ts ->
                        val dateText = remember(ts) {
                            SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ts * 1000L))
                        }
                        Text(
                            text = "Senaste handelsdagen: $dateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            return
        }

        val lastPrice = data.prices.last()
        val refPrice = data.previousClose ?: data.prices.first()
        val isPositive = lastPrice >= refPrice

        val lineColor = if (isPositive) LocalPriceUp.current else LocalPriceDown.current
        val fillColor = lineColor.copy(alpha = 0.12f)

        val textMeasurer = rememberTextMeasurer()
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

        val timeFormat = when (selectedPeriod) {
            ChartPeriod.DAY          -> SimpleDateFormat("HH:mm", Locale.getDefault())
            ChartPeriod.WEEK         -> SimpleDateFormat("EEE", Locale.getDefault())
            ChartPeriod.MONTH,
            ChartPeriod.THREE_MONTHS -> SimpleDateFormat("d MMM", Locale.getDefault())
            ChartPeriod.SIX_MONTHS   -> SimpleDateFormat("MMM", Locale.getDefault())
            ChartPeriod.YEAR         -> SimpleDateFormat("MMM yy", Locale.getDefault())
            ChartPeriod.FIVE_YEARS   -> SimpleDateFormat("yyyy", Locale.getDefault())
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 8.dp)
        ) {
            val prices = data.prices
            val timestamps = data.timestamps
            val minPrice = prices.min()
            val maxPrice = prices.max()
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.001)

            val yLabelWidth = 52.dp.toPx()
            val xLabelHeight = 18.dp.toPx()
            val topPadding = 8.dp.toPx()

            val chartLeft = yLabelWidth
            val chartTop = topPadding
            val chartRight = size.width
            val chartBottom = size.height - xLabelHeight
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            fun xFor(i: Int) = chartLeft + i * (chartWidth / (prices.size - 1).coerceAtLeast(1).toFloat())
            fun yFor(price: Double) = chartTop + (chartHeight * (1.0 - (price - minPrice) / priceRange)).toFloat()

            // --- Y-axeln: 3 prisetiketter (max, mitt, min) ---
            val yLabels = listOf(maxPrice, (minPrice + maxPrice) / 2.0, minPrice)
            yLabels.forEach { price ->
                val y = yFor(price)
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 0.5.dp.toPx()
                )
                val text = formatAxisPrice(price)
                val measured = textMeasurer.measure(text, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = labelStyle,
                    topLeft = Offset(
                        x = (yLabelWidth - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                        y = y - measured.size.height / 2f
                    )
                )
            }

            // --- X-axeln: 4 tidsetiketter jämnt fördelade ---
            val xLabelCount = 4
            val indexStep = (prices.size - 1).toFloat() / (xLabelCount - 1)
            for (idx in 0 until xLabelCount) {
                val i = (idx * indexStep).toInt().coerceIn(0, prices.size - 1)
                val x = xFor(i)
                val timeText = timeFormat.format(Date(timestamps[i] * 1000L))
                val measured = textMeasurer.measure(timeText, labelStyle)
                val labelX = when (idx) {
                    0 -> chartLeft
                    xLabelCount - 1 -> chartRight - measured.size.width
                    else -> x - measured.size.width / 2f
                }
                drawText(
                    textMeasurer = textMeasurer,
                    text = timeText,
                    style = labelStyle,
                    topLeft = Offset(
                        x = labelX.coerceIn(chartLeft, chartRight - measured.size.width),
                        y = chartBottom + 3.dp.toPx()
                    )
                )
            }

            // --- Graflinje och fyllning ---
            val fillPath = Path().apply {
                moveTo(xFor(0), yFor(prices[0]))
                for (i in 1 until prices.size) {
                    lineTo(xFor(i), yFor(prices[i]))
                }
                lineTo(xFor(prices.size - 1), chartBottom)
                lineTo(xFor(0), chartBottom)
                close()
            }
            drawPath(fillPath, color = fillColor)

            val linePath = Path().apply {
                moveTo(xFor(0), yFor(prices[0]))
                for (i in 1 until prices.size) {
                    lineTo(xFor(i), yFor(prices[i]))
                }
            }
            drawPath(
                linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Slutpunkt som cirkel
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(xFor(prices.size - 1), yFor(prices.last()))
            )
        }
    }
}

private fun formatAxisPrice(price: Double): String = when {
    price >= 10_000 -> "%.0f".format(price)
    price >= 1_000  -> "%.1f".format(price)
    else            -> "%.2f".format(price)
}
