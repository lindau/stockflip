package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.accessibilityLabel
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.IntradayChartData
import com.stockflip.SmaChartLevel
import com.stockflip.SmaPoint
import com.stockflip.StockDetailData
import com.stockflip.ui.components.CompanyLogoAvatar
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.LocalTextTertiary
import com.stockflip.ui.theme.NordikNumericStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ClarityStockDetailPanel(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    smaLevels: List<SmaChartLevel> = emptyList(),
    logoRefreshToken: Int = 0,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClarityStockHeroCard(
            data = data,
            chartData = chartData,
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onPeriodSelected,
            isLandscape = isLandscape,
            onFullscreenToggle = onFullscreenToggle,
            smaLevels = smaLevels,
            logoRefreshToken = logoRefreshToken,
        )
        ClarityStockStatsGrid(data = data)
        ClarityWeekRangeCard(data = data)
    }
}

@Composable
private fun ClarityStockHeroCard(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    isLandscape: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    smaLevels: List<SmaChartLevel> = emptyList(),
    logoRefreshToken: Int = 0,
) {
    val colorScheme = MaterialTheme.colorScheme
    val periodChange = calculatePeriodChange(
        data = data,
        chartData = chartData,
        selectedPeriod = selectedPeriod,
    )
    val changeIndicator = periodChange.percent ?: periodChange.delta
    val changeColor = when {
        changeIndicator == null -> colorScheme.onSurfaceVariant
        changeIndicator >= 0.0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val isPositive = changeIndicator == null || changeIndicator >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CompanyLogoAvatar(symbol = data.symbol, size = 48.dp, refreshToken = logoRefreshToken)

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = CountryFlagHelper.getFlagForExchange(data.exchange, data.currency).orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stockMeta(data),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = LocalTextTertiary.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = data.companyName,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.lastPrice?.let { CurrencyHelper.formatDecimal(it) } ?: "Laddar",
                    modifier = Modifier.weight(1f, fill = false),
                    style = NordikNumericStyle.copy(
                        fontSize = 44.sp,
                        lineHeight = 50.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DailyChangePill(
                    periodChange = periodChange,
                    changeColor = changeColor,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                ClaritySparkChart(
                    chartData = chartData,
                    isPositive = isPositive,
                    lineColor = changeColor,
                    selectedPeriod = selectedPeriod,
                    smaLevels = smaLevels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                )
                if (isLandscape && onFullscreenToggle != null) {
                    IconButton(
                        onClick = onFullscreenToggle,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "Visa graf i fullskärm",
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ClarityPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun DailyChangePill(
    periodChange: PeriodChange,
    changeColor: Color,
) {
    val lines = changeLines(periodChange)
    Column(
        modifier = Modifier
            .background(changeColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = changeColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ClaritySparkChart(
    chartData: IntradayChartData?,
    isPositive: Boolean,
    lineColor: Color,
    selectedPeriod: ChartPeriod,
    modifier: Modifier = Modifier,
    smaLevels: List<SmaChartLevel> = emptyList(),
) {
    val prices = chartData?.prices.orEmpty()
    val timestamps = chartData?.timestamps.orEmpty()
    if (prices.size < 2) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chartData?.emptyReason ?: "Laddar graf",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val fillColor = lineColor.copy(alpha = if (isPositive) 0.16f else 0.12f)
    val crosshairLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val crosshairRingColor = MaterialTheme.colorScheme.surface
    val tooltipBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tooltipDateColor = LocalTextTertiary.current
    val tooltipPriceColor = MaterialTheme.colorScheme.onSurface
    val smaLineColors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    val textMeasurer = rememberTextMeasurer()

    // Lokalt state (inte hissat) så bara denna Canvas ritas om under drag, inte resten av kortet.
    var touchIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(chartData, selectedPeriod) { touchIndex = null }

    Canvas(
        modifier = modifier.pointerInput(prices.size) {
            val lastIndex = prices.lastIndex.coerceAtLeast(1)
            fun indexForX(x: Float): Int {
                val step = size.width.toFloat() / lastIndex
                return (x / step).roundToInt().coerceIn(0, prices.lastIndex)
            }
            // awaitEachGesture (inte detectHorizontalDragGestures) så hårkorset visas direkt vid
            // nedtryck utan att kräva rörelse förbi touch-slop först.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                touchIndex = indexForX(down.position.x)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    touchIndex = indexForX(change.position.x)
                }
                touchIndex = null
            }
        },
    ) {
        // Priceskalan utökas för att alltid rymma SMA-nivåerna — annars klipps linjen tyst
        // utanför canvasen när priset ligger långt från det bevakade medelvärdet.
        val smaValues = smaLevels.flatMap { level -> level.points.map { it.value } }
        val minPrice = minOf(prices.min(), smaValues.minOrNull() ?: prices.min())
        val maxPrice = maxOf(prices.max(), smaValues.maxOrNull() ?: prices.max())
        val range = (maxPrice - minPrice).coerceAtLeast(0.001)
        val chartHeight = size.height * 0.88f
        val topPadding = size.height * 0.04f

        fun xFor(index: Int): Float = index * (size.width / (prices.lastIndex).coerceAtLeast(1).toFloat())
        fun yFor(price: Double): Float = topPadding + chartHeight * (1f - ((price - minPrice) / range).toFloat())

        val linePath = Path().apply {
            moveTo(xFor(0), yFor(prices[0]))
            for (index in 1..prices.lastIndex) {
                lineTo(xFor(index), yFor(prices[index]))
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xFor(prices.lastIndex), size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(xFor(prices.lastIndex), yFor(prices.last())),
        )

        // SMA är ett dagsstängningsbaserat mått: varje graf-punkt tilldelas den senaste kända
        // SMA-punkten vid eller före sin egen tidsstämpel ("carry forward"), så linjen rör sig
        // dag för dag i takt med priset i stället för att ritas som en enda vågrät linje.
        smaLevels.forEachIndexed { index, level ->
            val smaColor = smaLineColors[index % smaLineColors.size]
            val alignedValues = alignSmaToChart(timestamps, level.points)

            val smaPath = Path()
            var started = false
            var lastPoint: Offset? = null
            alignedValues.forEachIndexed { i, value ->
                if (value == null) return@forEachIndexed
                val point = Offset(xFor(i), yFor(value))
                if (!started) {
                    smaPath.moveTo(point.x, point.y)
                    started = true
                } else {
                    smaPath.lineTo(point.x, point.y)
                }
                lastPoint = point
            }
            if (!started) return@forEachIndexed

            drawPath(
                path = smaPath,
                color = smaColor.copy(alpha = 0.85f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f),
                ),
            )

            val label = "SMA${level.period}"
            val labelStyle = TextStyle(fontSize = 9.sp, color = smaColor, fontWeight = FontWeight.Bold)
            val labelMeasured = textMeasurer.measure(label, labelStyle)
            val anchor = lastPoint ?: Offset(0f, topPadding)
            val labelLeft = (anchor.x - labelMeasured.size.width - 6.dp.toPx())
                .coerceIn(0f, size.width - labelMeasured.size.width)
            val labelTop = (anchor.y - labelMeasured.size.height - 2.dp.toPx())
                .coerceIn(0f, size.height - labelMeasured.size.height)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle,
                topLeft = Offset(labelLeft, labelTop),
            )
        }

        val idx = touchIndex
        if (idx != null) {
            val crosshairX = xFor(idx)
            val crosshairY = yFor(prices[idx])

            drawLine(
                color = crosshairLineColor,
                start = Offset(crosshairX, 0f),
                end = Offset(crosshairX, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = crosshairLineColor,
                start = Offset(0f, crosshairY),
                end = Offset(size.width, crosshairY),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(color = crosshairRingColor, radius = 6.dp.toPx(), center = Offset(crosshairX, crosshairY))
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(crosshairX, crosshairY))

            val timestamp = chartData?.timestamps?.getOrNull(idx)
            val dateText = timestamp?.let { crosshairDateFormat(selectedPeriod).format(Date(it * 1000L)) }.orEmpty()
            val priceText = CurrencyHelper.formatDecimal(prices[idx])

            val dateStyle = TextStyle(fontSize = 9.sp, color = tooltipDateColor, fontWeight = FontWeight.Medium)
            val priceStyle = TextStyle(fontSize = 12.sp, color = tooltipPriceColor, fontWeight = FontWeight.Bold)
            val dateMeasured = textMeasurer.measure(dateText, dateStyle)
            val priceMeasured = textMeasurer.measure(priceText, priceStyle)

            val paddingX = 8.dp.toPx()
            val paddingY = 6.dp.toPx()
            val lineGap = 2.dp.toPx()
            val tooltipWidth = maxOf(dateMeasured.size.width, priceMeasured.size.width) + paddingX * 2
            val tooltipHeight = dateMeasured.size.height + priceMeasured.size.height + lineGap + paddingY * 2

            val gap = 10.dp.toPx()
            val preferredLeft = crosshairX + gap
            val tooltipLeft = if (preferredLeft + tooltipWidth <= size.width) {
                preferredLeft
            } else {
                (crosshairX - gap - tooltipWidth).coerceAtLeast(0f)
            }
            val tooltipTop = topPadding + 2.dp.toPx()

            drawRoundRect(
                color = tooltipBackgroundColor,
                topLeft = Offset(tooltipLeft, tooltipTop),
                size = Size(tooltipWidth, tooltipHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = dateText,
                style = dateStyle,
                topLeft = Offset(tooltipLeft + paddingX, tooltipTop + paddingY),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = priceText,
                style = priceStyle,
                topLeft = Offset(
                    x = tooltipLeft + paddingX,
                    y = tooltipTop + paddingY + dateMeasured.size.height + lineGap,
                ),
            )
        }
    }
}

/**
 * Slår ihop en SMA-serie (en punkt per dagsstängning) med kursgrafens egna tidsstämplar:
 * för varje graf-punkt väljs den senaste SMA-punkten vid eller före den tidsstämpeln
 * ("carry forward"), null tills den första SMA-punkten är tillgänglig. Båda listorna
 * antas vara kronologiskt sorterade, precis som Yahoo-svaren de kommer ifrån.
 */
private fun alignSmaToChart(chartTimestamps: List<Long>, smaPoints: List<SmaPoint>): List<Double?> {
    if (chartTimestamps.isEmpty() || smaPoints.isEmpty()) return List(chartTimestamps.size) { null }
    val aligned = arrayOfNulls<Double>(chartTimestamps.size)
    var smaIndex = 0
    var lastValue: Double? = null
    for (i in chartTimestamps.indices) {
        val ts = chartTimestamps[i]
        while (smaIndex < smaPoints.size && smaPoints[smaIndex].timestamp <= ts) {
            lastValue = smaPoints[smaIndex].value
            smaIndex++
        }
        aligned[i] = lastValue
    }
    return aligned.toList()
}

/**
 * Datumformat för crosshair-tooltipen, anpassat efter vald graf-period (svensk locale).
 */
private fun crosshairDateFormat(period: ChartPeriod): SimpleDateFormat = when (period) {
    ChartPeriod.DAY -> SimpleDateFormat("HH:mm", Locale("sv", "SE"))
    ChartPeriod.WEEK -> SimpleDateFormat("EEE d MMM", Locale("sv", "SE"))
    ChartPeriod.MONTH,
    ChartPeriod.THREE_MONTHS -> SimpleDateFormat("d MMM", Locale("sv", "SE"))
    ChartPeriod.SIX_MONTHS,
    ChartPeriod.YEAR,
    ChartPeriod.FIVE_YEARS -> SimpleDateFormat("MMM yyyy", Locale("sv", "SE"))
}

@Composable
private fun ClarityPeriodSelector(
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ChartPeriod.entries.forEach { period ->
            val selected = period == selectedPeriod
            Text(
                text = period.label,
                modifier = Modifier
                    .weight(1f)
                    // Tryckyta minst 48 dp (Material); den synliga pillen behåller sin storlek.
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colorScheme.onSurface else Color.Transparent)
                    // Tab-roll + selected så att skärmläsare säger vilken period som är vald.
                    .selectable(selected = selected, role = Role.Tab) { onPeriodSelected(period) }
                    .semantics { contentDescription = period.accessibilityLabel() }
                    .padding(vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) colorScheme.surface else colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Fullskärmsversion av kursgrafen, visad ovanpå resten av skärmen när telefonen
 * är i landskapsläge och användaren tryckt på fullskärmsknappen.
 */
@Composable
fun FullscreenStockChart(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    smaLevels: List<SmaChartLevel> = emptyList(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val periodChange = calculatePeriodChange(
        data = data,
        chartData = chartData,
        selectedPeriod = selectedPeriod,
    )
    val changeIndicator = periodChange.percent ?: periodChange.delta
    val changeColor = when {
        changeIndicator == null -> colorScheme.onSurfaceVariant
        changeIndicator >= 0.0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val isPositive = changeIndicator == null || changeIndicator >= 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stockMeta(data),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = LocalTextTertiary.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = data.lastPrice?.let { CurrencyHelper.formatDecimal(it) } ?: "Laddar",
                        style = NordikNumericStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                    DailyChangePill(periodChange = periodChange, changeColor = changeColor)
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.FullscreenExit,
                    contentDescription = "Stäng fullskärmsgraf",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        ClaritySparkChart(
            chartData = chartData,
            isPositive = isPositive,
            lineColor = changeColor,
            selectedPeriod = selectedPeriod,
            smaLevels = smaLevels,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
        )

        ClarityPeriodSelector(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onPeriodSelected,
        )
    }
}

@Composable
private fun ClarityStockStatsGrid(data: StockDetailData) {
    val hasMetrics = data.peRatio != null ||
        data.psRatio != null ||
        data.dividendYield != null ||
        data.earningsPerShare != null ||
        data.marketCap != null ||
        data.returnOnEquity != null
    val stats = if (hasMetrics) {
        listOf(
            "P/E" to (data.peRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "P/S" to (data.psRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "Direktavkastning" to (data.dividendYield?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
            "Vinst/aktie" to (data.earningsPerShare?.let { CurrencyHelper.formatPrice(it, data.currency) } ?: "-"),
            "Börsvärde" to (data.marketCap?.let { formatCompactMarketCap(it, data.currency) } ?: "-"),
            "ROE" to (data.returnOnEquity?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
        )
    } else {
        listOf(
            "52v högsta" to (data.week52High?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "52v lägsta" to (data.week52Low?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "Drawdown" to (data.drawdownPercent?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
        )
    }

    val columns = 3
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.chunked(columns).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowStats.forEach { (label, value) ->
                    ClarityStatCell(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowStats.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ClarityStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(Locale("sv", "SE")),
                modifier = Modifier.height(14.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = LocalTextTertiary.current,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 4.dp),
                style = NordikNumericStyle.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClarityWeekRangeCard(data: StockDetailData) {
    val colorScheme = MaterialTheme.colorScheme
    val low = data.week52Low
    val high = data.week52High
    val price = data.lastPrice
    val fraction = if (low != null && high != null && price != null && high > low) {
        ((price - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    } else {
        null
    }
    val markerColor = if (fraction != null) LocalPriceUp.current else colorScheme.onSurfaceVariant
    val markerInnerColor = colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "52 V INTERVALL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                ),
                color = LocalTextTertiary.current,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(top = 12.dp),
            ) {
                val trackHeight = 8.dp.toPx()
                val top = (size.height - trackHeight) / 2f
                val radius = trackHeight / 2f
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.16f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                val markerX = size.width * (fraction ?: 0.5f)
                drawCircle(
                    color = markerColor,
                    radius = 7.dp.toPx(),
                    center = Offset(markerX, size.height / 2f),
                )
                drawCircle(
                    color = markerInnerColor,
                    radius = 3.dp.toPx(),
                    center = Offset(markerX, size.height / 2f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RangeLabel(low?.let { CurrencyHelper.formatDecimal(it) } ?: "-")
                RangeLabel(price?.let { CurrencyHelper.formatDecimal(it) } ?: "-", emphasized = true)
                RangeLabel(high?.let { CurrencyHelper.formatDecimal(it) } ?: "-")
            }
        }
    }
}

@Composable
private fun RangeLabel(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = if (emphasized) MaterialTheme.colorScheme.onSurface else LocalTextTertiary.current,
    )
}

private fun stockMeta(data: StockDetailData): String {
    return listOfNotNull(
        data.symbol,
        data.exchange?.takeIf { it.isNotBlank() },
        data.currency.takeIf { it.isNotBlank() },
    ).joinToString(" · ").uppercase(Locale("sv", "SE"))
}

private fun formatCompactMarketCap(value: Double, currency: String): String {
    val absoluteValue = abs(value)
    val (scaledValue, unit) = when {
        absoluteValue >= 1_000_000_000_000.0 -> value / 1_000_000_000_000.0 to "bilj"
        absoluteValue >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "mdr"
        absoluteValue >= 1_000_000.0 -> value / 1_000_000.0 to "mn"
        else -> return CurrencyHelper.formatPrice(value, currency)
    }
    val formatter = java.text.DecimalFormat(
        if (abs(scaledValue) >= 100.0) "#,##0" else "#,##0.#",
        java.text.DecimalFormatSymbols(Locale("sv", "SE")),
    )
    val number = formatter.format(scaledValue)
    val currencySymbol = CurrencyHelper.getCurrencySymbol(currency)
    return when (currency.uppercase(Locale.ROOT)) {
        "USD", "EUR", "GBP", "JPY", "CNY", "CHF", "CAD", "AUD" -> "$currencySymbol$number $unit"
        else -> "$number $unit $currencySymbol"
    }
}

internal data class PeriodChange(
    val delta: Double?,
    val percent: Double?,
)

internal fun calculatePeriodChange(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
): PeriodChange {
    if (selectedPeriod == ChartPeriod.DAY) {
        return calculateDailyChange(data, chartData)
    }

    if (chartData == null || chartData.emptyReason != null) {
        return PeriodChange(delta = null, percent = null)
    }

    val firstPrice = chartData.prices.firstOrNull()?.takeIf { it > 0.0 }
    val lastPrice = chartData.prices.lastOrNull()
    return calculateChange(firstPrice, lastPrice)
}

private fun calculateDailyChange(
    data: StockDetailData,
    chartData: IntradayChartData?,
): PeriodChange {
    val lastPrice = data.lastPrice ?: chartData?.prices?.lastOrNull()
    val previousClose = data.previousClose ?: chartData?.previousClose
    val delta = if (lastPrice != null && previousClose != null) {
        lastPrice - previousClose
    } else {
        null
    }
    val percent = data.dailyChangePercent ?: if (lastPrice != null && previousClose != null && previousClose > 0.0) {
        ((lastPrice - previousClose) / previousClose) * 100.0
    } else {
        null
    }
    return PeriodChange(delta = delta, percent = percent)
}

private fun calculateChange(
    firstPrice: Double?,
    lastPrice: Double?,
): PeriodChange {
    if (firstPrice == null || lastPrice == null || firstPrice <= 0.0) {
        return PeriodChange(delta = null, percent = null)
    }
    val delta = lastPrice - firstPrice
    return PeriodChange(
        delta = delta,
        percent = (delta / firstPrice) * 100.0,
    )
}

private fun changeLines(change: PeriodChange): List<String> {
    val deltaText = change.delta?.let { formatSignedChange(it) }
    val percentText = change.percent?.let { "${formatSignedChange(it)} %" } ?: "— %"
    return listOfNotNull(deltaText, percentText)
}

private fun formatSignedChange(value: Double): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    return "$sign${CurrencyHelper.formatDecimal(abs(value))}"
}
