package com.stockflip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.CurrencyHelper
import com.stockflip.PairChartData
import com.stockflip.PairChartSeries
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class PairSignalState {
    FAR,
    NEAR,
    TRIGGERED
}

private enum class SeriesStyle {
    SOLID_CIRCLE,
    DASHED_SQUARE
}

private val PairSeriesAColor = Color(0xFF0057D9)
private val PairSeriesBColor = Color(0xFFD97706)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairPerformanceChart(
    data: PairChartData,
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
                    label = { Text(text = period.label, style = MaterialTheme.typography.labelSmall) },
                    icon = {}
                )
            }
        }

        if (data.normalizedA.values.isEmpty() || data.normalizedB.values.isEmpty()) {
            EmptyChartState(
                reason = data.emptyReason ?: data.spread.emptyReason ?: "Ingen data",
                timestamp = data.lastTradeTimestamp
            )
            return
        }

        if (data.emptyReason != null || data.spread.emptyReason != null) {
            Text(
                text = data.emptyReason ?: data.spread.emptyReason ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        NormalizedComparisonChart(
            seriesA = data.normalizedA,
            seriesB = data.normalizedB,
            stockALabel = data.stockALabel,
            stockBLabel = data.stockBLabel,
            selectedPeriod = selectedPeriod,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "Spread och signal",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
        )

        SpreadMiniChart(
            data = data,
            selectedPeriod = selectedPeriod
        )

        data.lastTradeTimestamp?.let { ts ->
            val updatedText = remember(ts) {
                SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(ts * 1000L))
            }
            Text(
                text = "Senast uppdaterad: $updatedText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun LegendRow(
    stockALabel: String,
    stockBLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = PairSeriesAColor, label = stockALabel, style = SeriesStyle.SOLID_CIRCLE)
        LegendItem(color = PairSeriesBColor, label = stockBLabel, style = SeriesStyle.DASHED_SQUARE)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, style: SeriesStyle) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color,
                    if (style == SeriesStyle.SOLID_CIRCLE) RoundedCornerShape(50) else RoundedCornerShape(2.dp)
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NormalizedComparisonChart(
    seriesA: PairChartSeries,
    seriesB: PairChartSeries,
    stockALabel: String,
    stockBLabel: String,
    selectedPeriod: ChartPeriod,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val primaryColor = PairSeriesAColor
    val secondaryColor = PairSeriesBColor
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val timestamps = seriesA.timestamps.ifEmpty { seriesB.timestamps }
    val values = seriesA.values + seriesB.values + 100.0
    val minValue = values.minOrNull() ?: 100.0
    val maxValue = values.maxOrNull() ?: 100.0
    val chartMin = min(minValue, 100.0)
    val chartMax = max(maxValue, 100.0)
    val range = (chartMax - chartMin).coerceAtLeast(0.5)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val yLabelWidth = 54.dp.toPx()
            val rightPadding = 18.dp.toPx()
            val xLabelHeight = 20.dp.toPx()
            val chartLeft = yLabelWidth
            val chartTop = 34.dp.toPx()
            val chartRight = size.width - rightPadding
            val chartBottom = size.height - xLabelHeight
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            fun xFor(index: Int, count: Int): Float {
                return chartLeft + index * (chartWidth / (count - 1).coerceAtLeast(1).toFloat())
            }

            fun yFor(value: Double): Float {
                return chartTop + (chartHeight * (1.0 - (value - chartMin) / range)).toFloat()
            }

            val yTicks = listOf(chartMax, 100.0, chartMin).distinct()
            yTicks.forEach { tick ->
                val y = yFor(tick)
                drawLine(
                    color = if (abs(tick - 100.0) < 0.01) baselineColor else gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = if (abs(tick - 100.0) < 0.01) 1.2.dp.toPx() else 0.9.dp.toPx()
                )
                val text = CurrencyHelper.formatDecimal(tick)
                val measured = textMeasurer.measure(text, labelStyle)
                val maxLabelY = (chartBottom - measured.size.height).coerceAtLeast(chartTop)
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = labelStyle,
                    topLeft = Offset(
                        x = (yLabelWidth - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                        y = (y - measured.size.height / 2f).coerceIn(chartTop, maxLabelY)
                    )
                )
            }

            drawSeries(
                values = seriesA.values,
                color = primaryColor,
                chartLeft = chartLeft,
                chartTop = chartTop,
                chartBottom = chartBottom,
                chartWidth = chartWidth,
                minValue = chartMin,
                range = range,
                style = SeriesStyle.SOLID_CIRCLE
            )
            drawSeries(
                values = seriesB.values,
                color = secondaryColor,
                chartLeft = chartLeft,
                chartTop = chartTop,
                chartBottom = chartBottom,
                chartWidth = chartWidth,
                minValue = chartMin,
                range = range,
                style = SeriesStyle.DASHED_SQUARE
            )

            buildXLabels(timestamps, selectedPeriod).forEach { (index, label) ->
                val x = xFor(index, timestamps.size)
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(
                        x = (x - measured.size.width / 2f).coerceIn(chartLeft, chartRight - measured.size.width),
                        y = chartBottom + 3.dp.toPx()
                    )
                )
            }

            listOf(
                Triple(stockALabel, seriesA.values.last(), primaryColor),
                Triple(stockBLabel, seriesB.values.last(), secondaryColor)
            ).map { (label, value, color) ->
                val measured = textMeasurer.measure(label, labelStyle)
                val maxLabelY = (chartBottom - measured.size.height).coerceAtLeast(chartTop)
                Triple(
                    measured,
                    Offset(
                        x = (chartRight - measured.size.width - 4.dp.toPx()).coerceAtLeast(chartLeft),
                        y = (yFor(value) - measured.size.height / 2f)
                            .coerceIn(chartTop, maxLabelY)
                    ),
                    color
                )
            }.let { positioned ->
                val separated = separateLabelPositions(
                    items = positioned.map { Triple(it.first, it.second.x, it.second.y) },
                    minGap = 6.dp.toPx(),
                    minY = chartTop,
                    maxY = chartBottom
                )
                separated.forEachIndexed { index, adjusted ->
                    val (measured, _, color) = positioned[index]
                    val label = if (index == 0) stockALabel else stockBLabel
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = labelStyle.copy(color = color),
                        topLeft = Offset(
                            x = adjusted.x,
                            y = adjusted.y
                        )
                    )
                }
            }
        }

        LegendRow(
            stockALabel = stockALabel,
            stockBLabel = stockBLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 60.dp, top = 8.dp)
        )
    }
}

@Composable
private fun SpreadMiniChart(
    data: PairChartData,
    selectedPeriod: ChartPeriod
) {
    val prices = data.spread.prices
    val timestamps = data.spread.timestamps
    if (prices.isEmpty() || timestamps.isEmpty()) {
        EmptyChartState(
            reason = data.spread.emptyReason ?: "Ingen data",
            timestamp = data.lastTradeTimestamp
        )
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val lineColor = if ((data.currentSpread ?: 0.0) >= 0.0) LocalPriceUp.current else LocalPriceDown.current
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val equalLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val chartSurfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val nearColor = tertiaryColor
    val triggeredColor = MaterialTheme.colorScheme.error
    val currentSpreadAbs = abs(data.currentSpread ?: 0.0)
    val target = data.spreadTarget
    val signalState = remember(data.currentSpread, data.spreadTarget) {
        when {
            target == null || target <= 0.0 -> PairSignalState.FAR
            currentSpreadAbs >= target -> PairSignalState.TRIGGERED
            currentSpreadAbs >= target * 0.85 -> PairSignalState.NEAR
            else -> PairSignalState.FAR
        }
    }
    val statusColor = when (signalState) {
        PairSignalState.FAR -> lineColor
        PairSignalState.NEAR -> nearColor
        PairSignalState.TRIGGERED -> triggeredColor
    }
    val backdropColor = chartSurfaceColor
    val statusBandColor = when (signalState) {
        PairSignalState.FAR -> statusColor.copy(alpha = 0.18f)
        PairSignalState.NEAR -> statusColor.copy(alpha = 0.24f)
        PairSignalState.TRIGGERED -> statusColor.copy(alpha = 0.28f)
    }
    val allValues = buildList {
        addAll(prices)
        data.spreadTarget?.let {
            add(it)
            add(-it)
        }
        if (data.showEqualLine) add(0.0)
    }
    val minValue = allValues.minOrNull() ?: 0.0
    val maxValue = allValues.maxOrNull() ?: 0.0
    val range = (maxValue - minValue).coerceAtLeast(0.5)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
    ) {
        val yLabelWidth = 54.dp.toPx()
        val rightPadding = 18.dp.toPx()
        val xLabelHeight = 18.dp.toPx()
        val chartLeft = yLabelWidth
        val chartTop = 8.dp.toPx()
        val chartRight = size.width - rightPadding
        val chartBottom = size.height - xLabelHeight
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun xFor(index: Int): Float {
            return chartLeft + index * (chartWidth / (prices.size - 1).coerceAtLeast(1).toFloat())
        }

        fun yFor(value: Double): Float {
            return chartTop + (chartHeight * (1.0 - (value - minValue) / range)).toFloat()
        }

        drawRect(
            color = backdropColor,
            topLeft = Offset(chartLeft, chartTop),
            size = androidx.compose.ui.geometry.Size(chartWidth, chartHeight)
        )
        drawRect(
            color = statusBandColor,
            topLeft = Offset(chartLeft, chartTop),
            size = androidx.compose.ui.geometry.Size(chartWidth, 8.dp.toPx())
        )

        target?.let { spreadTarget ->
            val zoneHalfHeight = max(spreadTarget * 0.12, range * 0.035)
            listOf(spreadTarget, -spreadTarget).forEach { guide ->
                val top = yFor(guide + zoneHalfHeight)
                val bottom = yFor(guide - zoneHalfHeight)
                drawRect(
                    color = when (signalState) {
                        PairSignalState.TRIGGERED -> triggeredColor.copy(alpha = 0.10f)
                        PairSignalState.NEAR -> nearColor.copy(alpha = 0.09f)
                        PairSignalState.FAR -> tertiaryColor.copy(alpha = 0.06f)
                    },
                    topLeft = Offset(chartLeft, min(top, bottom)),
                    size = androidx.compose.ui.geometry.Size(chartWidth, abs(bottom - top))
                )
            }
        }

        buildList {
            add(maxValue)
            if (data.showEqualLine || (minValue < 0.0 && maxValue > 0.0)) add(0.0)
            add(minValue)
        }.distinct().forEach { tick ->
            val y = yFor(tick)
            drawLine(
                color = if (tick == 0.0) baselineColor else gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = if (tick == 0.0) 1.2.dp.toPx() else 0.9.dp.toPx()
            )
            val text = formatSignedDecimal(tick)
            val measured = textMeasurer.measure(text, labelStyle)
            val maxLabelY = (chartBottom - measured.size.height).coerceAtLeast(chartTop)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = labelStyle,
                topLeft = Offset(
                    x = (yLabelWidth - measured.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                    y = (y - measured.size.height / 2f).coerceIn(chartTop, maxLabelY)
                )
            )
        }

        data.spreadTarget?.let { target ->
            listOf(target, -target).forEach { guide ->
                val y = yFor(guide)
                drawLine(
                    color = if (signalState == PairSignalState.TRIGGERED) triggeredColor else tertiaryColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
        }

        if (data.showEqualLine) {
            val y = yFor(0.0)
            drawLine(
                color = equalLineColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.4.dp.toPx()
            )
        }

        val fillPath = Path().apply {
            moveTo(xFor(0), yFor(prices.first()))
            for (i in 1 until prices.size) {
                lineTo(xFor(i), yFor(prices[i]))
            }
            lineTo(xFor(prices.size - 1), chartBottom)
            lineTo(xFor(0), chartBottom)
            close()
        }
        drawPath(fillPath, color = statusColor.copy(alpha = 0.14f))

        drawSeries(
            values = prices,
            color = statusColor,
            chartLeft = chartLeft,
            chartTop = chartTop,
            chartBottom = chartBottom,
            chartWidth = chartWidth,
            minValue = minValue,
            range = range,
            style = SeriesStyle.SOLID_CIRCLE
        )

        val latestX = xFor(prices.lastIndex)
        val latestY = yFor(prices.last())
        drawCircle(
            color = surfaceColor,
            radius = 6.dp.toPx(),
            center = Offset(latestX, latestY)
        )
        drawCircle(
            color = statusColor,
            radius = 4.dp.toPx(),
            center = Offset(latestX, latestY)
        )
        val latestLabel = when (signalState) {
            PairSignalState.TRIGGERED -> "Larm"
            PairSignalState.NEAR -> "Nära"
            PairSignalState.FAR -> "Nu"
        }
        val latestMeasured = textMeasurer.measure(
            latestLabel,
            labelStyle.copy(color = surfaceColor)
        )
        val latestLabelX = when {
            latestX + latestMeasured.size.width + 20.dp.toPx() <= chartRight -> latestX + 10.dp.toPx()
            else -> (latestX - latestMeasured.size.width - 20.dp.toPx()).coerceAtLeast(chartLeft)
        }
        val latestLabelY = when {
            latestY - latestMeasured.size.height - 12.dp.toPx() >= chartTop -> latestY - latestMeasured.size.height - 12.dp.toPx()
            latestY + 12.dp.toPx() <= chartBottom - latestMeasured.size.height -> latestY + 12.dp.toPx()
            else -> (latestY - latestMeasured.size.height / 2f)
                .coerceIn(chartTop, chartBottom - latestMeasured.size.height)
        }
        val badgePaddingX = 6.dp.toPx()
        val badgePaddingY = 3.dp.toPx()
        drawRoundRect(
            color = statusColor,
            topLeft = Offset(
                x = latestLabelX - badgePaddingX,
                y = latestLabelY - badgePaddingY
            ),
            size = androidx.compose.ui.geometry.Size(
                latestMeasured.size.width + badgePaddingX * 2,
                latestMeasured.size.height + badgePaddingY * 2
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawText(
            textMeasurer = textMeasurer,
            text = latestLabel,
            style = labelStyle.copy(color = surfaceColor),
            topLeft = Offset(
                x = latestLabelX,
                y = latestLabelY
            )
        )

        buildXLabels(timestamps, selectedPeriod).forEach { (index, label) ->
            val x = xFor(index)
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle,
                topLeft = Offset(
                    x = (x - measured.size.width / 2f).coerceIn(chartLeft, chartRight - measured.size.width),
                    y = chartBottom + 3.dp.toPx()
                )
            )
        }
    }
}

private fun DrawScope.drawSeries(
    values: List<Double>,
    color: Color,
    chartLeft: Float,
    chartTop: Float,
    chartBottom: Float,
    chartWidth: Float,
    minValue: Double,
    range: Double,
    style: SeriesStyle
) {
    fun xFor(index: Int): Float {
        return chartLeft + index * (chartWidth / (values.size - 1).coerceAtLeast(1).toFloat())
    }

    fun yFor(value: Double): Float {
        return chartTop + ((chartBottom - chartTop) * (1.0 - (value - minValue) / range)).toFloat()
    }

    val linePath = Path().apply {
        moveTo(xFor(0), yFor(values[0]))
        for (i in 1 until values.size) {
            lineTo(xFor(i), yFor(values[i]))
        }
    }
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = if (style == SeriesStyle.SOLID_CIRCLE) 2.4.dp.toPx() else 2.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (style == SeriesStyle.DASHED_SQUARE) {
                PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()))
            } else {
                null
            }
        )
    )
    val endCenter = Offset(xFor(values.size - 1), yFor(values.last()))
    if (style == SeriesStyle.SOLID_CIRCLE) {
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = endCenter
        )
    } else {
        val half = 3.5.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(endCenter.x - half, endCenter.y - half),
            size = androidx.compose.ui.geometry.Size(half * 2, half * 2)
        )
    }
}

@Composable
private fun EmptyChartState(reason: String, timestamp: Long?) {
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
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            timestamp?.let { ts ->
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
}

private fun buildXLabels(timestamps: List<Long>, period: ChartPeriod): List<Pair<Int, String>> {
    if (timestamps.isEmpty()) return emptyList()
    val format = chartTimeFormat(period)
    val count = 4
    val step = (timestamps.size - 1).toFloat() / (count - 1)
    return (0 until count).map { idx ->
        val index = (idx * step).toInt().coerceIn(0, timestamps.lastIndex)
        index to format.format(Date(timestamps[index] * 1000L))
    }.distinctBy { it.first }
}

private fun separateLabelPositions(
    items: List<Triple<androidx.compose.ui.text.TextLayoutResult, Float, Float>>,
    minGap: Float,
    minY: Float,
    maxY: Float
): List<Offset> {
    if (items.isEmpty()) return emptyList()

    val sorted = items.mapIndexed { index, (measured, x, y) ->
        val maxAllowedY = (maxY - measured.size.height).coerceAtLeast(minY)
        Pair(index, Triple(measured, x, y.coerceIn(minY, maxAllowedY)))
    }.sortedBy { it.second.third }

    val adjusted = mutableListOf<Pair<Int, Triple<androidx.compose.ui.text.TextLayoutResult, Float, Float>>>()
    sorted.forEach { (index, item) ->
        val (measured, x, desiredY) = item
        val previous = adjusted.lastOrNull()
        val minAllowedY = previous?.let { it.second.third + it.second.first.size.height + minGap } ?: minY
        val maxAllowedY = (maxY - measured.size.height).coerceAtLeast(minY)
        adjusted += index to Triple(
            measured,
            x,
            desiredY.coerceAtLeast(minAllowedY).coerceAtMost(maxAllowedY)
        )
    }

    for (i in adjusted.indices.reversed()) {
        val (index, current) = adjusted[i]
        val maxAllowedY = if (i == adjusted.lastIndex) {
            (maxY - current.first.size.height).coerceAtLeast(minY)
        } else {
            (adjusted[i + 1].second.third - current.first.size.height - minGap).coerceAtLeast(minY)
        }
        adjusted[i] = index to Triple(current.first, current.second, current.third.coerceAtMost(maxAllowedY))
    }

    return adjusted
        .sortedBy { it.first }
        .map { (_, item) ->
            val (measured, x, y) = item
            val maxAllowedY = (maxY - measured.size.height).coerceAtLeast(minY)
            Offset(
                x = x,
                y = y.coerceIn(minY, maxAllowedY)
            )
        }
}

private fun chartTimeFormat(selectedPeriod: ChartPeriod): SimpleDateFormat = when (selectedPeriod) {
    ChartPeriod.DAY -> SimpleDateFormat("HH:mm", Locale.getDefault())
    ChartPeriod.WEEK -> SimpleDateFormat("EEE", Locale.getDefault())
    ChartPeriod.MONTH,
    ChartPeriod.THREE_MONTHS -> SimpleDateFormat("d MMM", Locale.getDefault())
    ChartPeriod.SIX_MONTHS -> SimpleDateFormat("MMM", Locale.getDefault())
    ChartPeriod.YEAR -> SimpleDateFormat("MMM yy", Locale.getDefault())
    ChartPeriod.FIVE_YEARS -> SimpleDateFormat("yyyy", Locale.getDefault())
}

private fun formatSignedDecimal(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${CurrencyHelper.formatDecimal(value)}"
}
