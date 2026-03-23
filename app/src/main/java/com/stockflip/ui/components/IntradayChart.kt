package com.stockflip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.stockflip.IntradayChartData
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntradayChart(
    data: IntradayChartData,
    modifier: Modifier = Modifier
) {
    val lastPrice = data.prices.last()
    val refPrice = data.previousClose ?: data.prices.first()
    val isPositive = lastPrice >= refPrice

    val lineColor = if (isPositive) LocalPriceUp.current else LocalPriceDown.current
    val fillColor = lineColor.copy(alpha = 0.12f)

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "Idag",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)  // Höjd ökar lite för att ge plats åt X-axeletiketter
        ) {
            val prices = data.prices
            val timestamps = data.timestamps
            val minPrice = prices.min()
            val maxPrice = prices.max()
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.001)

            // Reservera utrymme för etiketter
            val yLabelWidth = 52.dp.toPx()   // Vänstermarginal för prisetiketter
            val xLabelHeight = 18.dp.toPx()  // Nedre margin för tidsetiketter
            val topPadding = 8.dp.toPx()     // Lite luft i toppen

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
                // Tunn horisontell hjälplinje
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 0.5.dp.toPx()
                )
                // Prisetikett högercentrerad i vänstermarginalen
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
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val xLabelCount = 4
            val indexStep = (prices.size - 1).toFloat() / (xLabelCount - 1)
            for (idx in 0 until xLabelCount) {
                val i = (idx * indexStep).toInt().coerceIn(0, prices.size - 1)
                val x = xFor(i)
                val timeText = timeFormat.format(Date(timestamps[i] * 1000L))
                val measured = textMeasurer.measure(timeText, labelStyle)
                // Vänsterjustera första, högerjustera sista, centrera övriga
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
