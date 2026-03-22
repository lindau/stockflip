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
import androidx.compose.ui.unit.dp
import com.stockflip.IntradayChartData
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp

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
                .height(140.dp)
        ) {
            val prices = data.prices
            val minPrice = prices.min()
            val maxPrice = prices.max()
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.001)

            val stepX = size.width / (prices.size - 1).coerceAtLeast(1).toFloat()

            fun xFor(i: Int) = i * stepX
            fun yFor(price: Double) = (size.height * (1.0 - (price - minPrice) / priceRange)).toFloat()

            // Fill area under line
            val fillPath = Path().apply {
                moveTo(xFor(0), yFor(prices[0]))
                for (i in 1 until prices.size) {
                    lineTo(xFor(i), yFor(prices[i]))
                }
                lineTo(xFor(prices.size - 1), size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fillPath, color = fillColor)

            // Draw line
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

            // End dot
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(xFor(prices.size - 1), yFor(prices.last()))
            )
        }
    }
}
