package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
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
import com.stockflip.BollingerPoint
import com.stockflip.ChartIndicatorConfig
import com.stockflip.ChartPeriod
import com.stockflip.RsiPoint
import com.stockflip.TechnicalIndicators
import com.stockflip.supportsIndicators
import com.stockflip.accessibilityLabel
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.IntradayChartData
import com.stockflip.NextEarningsInfo
import com.stockflip.SmaChartLevel
import com.stockflip.SmaPoint
import com.stockflip.StockDetailData
import com.stockflip.ui.components.CompanyLogoAvatar
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.LocalTextTertiary
import com.stockflip.ui.theme.NumericStyle
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
    indicatorConfig: ChartIndicatorConfig = ChartIndicatorConfig(),
    bollingerPoints: List<BollingerPoint> = emptyList(),
    rsiPoints: List<RsiPoint> = emptyList(),
    onIndicatorConfigChange: ((ChartIndicatorConfig) -> Unit)? = null,
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
            indicatorConfig = indicatorConfig,
            bollingerPoints = bollingerPoints,
            rsiPoints = rsiPoints,
            onIndicatorConfigChange = onIndicatorConfigChange,
        )
        ClarityWeekRangeCard(data = data)
        ClarityStockStatsGrid(data = data)
        ClarityAnalystTargetCard(data = data)
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
    indicatorConfig: ChartIndicatorConfig = ChartIndicatorConfig(),
    bollingerPoints: List<BollingerPoint> = emptyList(),
    rsiPoints: List<RsiPoint> = emptyList(),
    onIndicatorConfigChange: ((ChartIndicatorConfig) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showSettings by remember { mutableStateOf(false) }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = CountryFlagHelper.getFlagForExchange(data.exchange, data.currency).orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stockMeta(data),
                            modifier = Modifier.weight(1f),
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
                        // Kugghjul och fullskärm på tickerns rad, längst till höger.
                        ChartActionButtons(
                            onFullscreenToggle = onFullscreenToggle,
                            onOpenSettings = if (onIndicatorConfigChange != null) ({ showSettings = true }) else null,
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
                    style = NumericStyle.copy(
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

            earningsLabel(data.nextEarnings)?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                ClarityChartWithIndicators(
                    chartData = chartData,
                    isPositive = isPositive,
                    lineColor = changeColor,
                    selectedPeriod = selectedPeriod,
                    smaLevels = smaLevels,
                    indicatorConfig = indicatorConfig,
                    bollingerPoints = bollingerPoints,
                    rsiPoints = rsiPoints,
                    modifier = Modifier.fillMaxWidth(),
                    chartHeight = 112.dp,
                )
            }

            ClarityPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    if (showSettings && onIndicatorConfigChange != null) {
        ChartSettingsDialog(
            config = indicatorConfig,
            canShowIndicators = selectedPeriod.supportsIndicators(),
            onChange = onIndicatorConfigChange,
            onDismiss = { showSettings = false },
        )
    }
}

/** Kugghjul (indikatorval) och fullskärmsknapp, placerade längst till höger på tickerns rad. */
@Composable
private fun ChartActionButtons(
    onFullscreenToggle: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onOpenSettings != null) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .background(colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Välj indikatorer i grafen",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onFullscreenToggle != null) {
            IconButton(
                onClick = onFullscreenToggle,
                modifier = Modifier
                    .size(36.dp)
                    .background(colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Fullscreen,
                    contentDescription = "Visa graf i fullskärm",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Dialog med en switch per indikator. Bollinger och RSI är bara valbara för 1M och längre. */
@Composable
internal fun ChartSettingsDialog(
    config: ChartIndicatorConfig,
    canShowIndicators: Boolean,
    onChange: (ChartIndicatorConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Visa i grafen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IndicatorSwitchRow(
                    label = "SMA (från bevakningar)",
                    checked = config.showSma,
                    enabled = true,
                    onCheckedChange = { onChange(config.copy(showSma = it)) },
                )
                IndicatorSwitchRow(
                    label = "Bollinger Bands (${TechnicalIndicators.DEFAULT_BOLLINGER_PERIOD}, 2σ)",
                    checked = config.showBollinger,
                    enabled = canShowIndicators,
                    onCheckedChange = { onChange(config.copy(showBollinger = it)) },
                )
                IndicatorSwitchRow(
                    label = "RSI (${TechnicalIndicators.DEFAULT_RSI_PERIOD})",
                    checked = config.showRsi,
                    enabled = canShowIndicators,
                    onCheckedChange = { onChange(config.copy(showRsi = it)) },
                )
                if (!canShowIndicators) {
                    Text(
                        text = "Bollinger och RSI visas för 1M och längre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Klar")
                }
            }
        }
    }
}

@Composable
private fun IndicatorSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    bollingerPoints: List<BollingerPoint> = emptyList(),
    watchLevels: List<Double> = emptyList(),
    touchIndex: Int? = null,
    onTouchIndexChange: (Int?) -> Unit = {},
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
    val bollingerColor = MaterialTheme.colorScheme.primary
    val watchLevelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val textMeasurer = rememberTextMeasurer()

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
                onTouchIndexChange(indexForX(down.position.x))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    onTouchIndexChange(indexForX(change.position.x))
                }
                onTouchIndexChange(null)
            }
        },
    ) {
        // Priceskalan utökas för att alltid rymma SMA-nivåerna — annars klipps linjen tyst
        // utanför canvasen när priset ligger långt från det bevakade medelvärdet.
        val bandValues = bollingerPoints.flatMap { listOf(it.upper, it.lower) }
        // Bevakningsnivåer långt från kurserna (mer än halva prisspannet utanför) skulle platta ut
        // kurslinjen, så de skalas inte in och ritas inte.
        val priceSpan = (prices.max() - prices.min()).coerceAtLeast(prices.max() * 0.002)
        val visibleWatchLevels = watchLevels.filter { it >= prices.min() - priceSpan * 0.5 && it <= prices.max() + priceSpan * 0.5 }
        val smaValues = smaLevels.flatMap { level -> level.points.map { it.value } } + bandValues + visibleWatchLevels
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

        // Bevakningsnivåer: tunn streckad linje över hela bredden.
        visibleWatchLevels.forEach { level ->
            val y = yFor(level)
            drawLine(
                color = watchLevelColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
            )
        }

        // Bollinger Bands ritas under kurslinjen: ifyllt band mellan övre/undre, streckade
        // ytterlinjer och en tunn mittlinje (SMA). Samma carry-forward som för SMA-linjerna.
        if (bollingerPoints.isNotEmpty()) {
            val upper = alignSeriesToChart(timestamps, bollingerPoints) { it.timestamp }
            val firstIndex = upper.indexOfFirst { it != null }
            if (firstIndex >= 0) {
                val upperPath = Path()
                val lowerPath = Path()
                val middlePath = Path()
                val bandPath = Path()
                for (i in firstIndex..upper.lastIndex) {
                    val point = upper[i] ?: continue
                    val x = xFor(i)
                    if (i == firstIndex) {
                        upperPath.moveTo(x, yFor(point.upper))
                        lowerPath.moveTo(x, yFor(point.lower))
                        middlePath.moveTo(x, yFor(point.middle))
                        bandPath.moveTo(x, yFor(point.upper))
                    } else {
                        upperPath.lineTo(x, yFor(point.upper))
                        lowerPath.lineTo(x, yFor(point.lower))
                        middlePath.lineTo(x, yFor(point.middle))
                        bandPath.lineTo(x, yFor(point.upper))
                    }
                }
                for (i in upper.lastIndex downTo firstIndex) {
                    val point = upper[i] ?: continue
                    bandPath.lineTo(xFor(i), yFor(point.lower))
                }
                bandPath.close()
                drawPath(bandPath, color = bollingerColor.copy(alpha = 0.08f))
                val dashed = Stroke(
                    width = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                )
                drawPath(upperPath, color = bollingerColor.copy(alpha = 0.7f), style = dashed)
                drawPath(lowerPath, color = bollingerColor.copy(alpha = 0.7f), style = dashed)
                drawPath(
                    middlePath,
                    color = bollingerColor.copy(alpha = 0.45f),
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

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
            val alignedValues = alignSeriesToChart(timestamps, level.points) { it.timestamp }.map { it?.value }

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
 * Slår ihop en indikatorserie (en punkt per dagsstängning) med kursgrafens egna tidsstämplar:
 * för varje graf-punkt väljs den senaste punkten vid eller före den tidsstämpeln
 * ("carry forward"), null tills den första punkten är tillgänglig. Båda listorna
 * antas vara kronologiskt sorterade, precis som Yahoo-svaren de kommer ifrån.
 */
internal fun <T> alignSeriesToChart(
    chartTimestamps: List<Long>,
    points: List<T>,
    timestampOf: (T) -> Long,
): List<T?> {
    if (chartTimestamps.isEmpty() || points.isEmpty()) return List(chartTimestamps.size) { null }
    val aligned = ArrayList<T?>(chartTimestamps.size)
    var pointIndex = 0
    var last: T? = null
    for (ts in chartTimestamps) {
        while (pointIndex < points.size && timestampOf(points[pointIndex]) <= ts) {
            last = points[pointIndex]
            pointIndex++
        }
        aligned.add(last)
    }
    return aligned
}

/**
 * Kursgraf med valfria indikatorer: Bollinger ritas i själva grafen, RSI i en egen panel under.
 * Hårkorset (touchIndex) hissas hit så RSI-panelen kan visa värdet vid samma punkt.
 * [chartHeight] null betyder att grafen fyller resterande höjd (fullskärm).
 */
@Composable
private fun ColumnScope.ClarityChartWithIndicatorsBody(
    chartData: IntradayChartData?,
    isPositive: Boolean,
    lineColor: Color,
    selectedPeriod: ChartPeriod,
    smaLevels: List<SmaChartLevel>,
    indicatorConfig: ChartIndicatorConfig,
    bollingerPoints: List<BollingerPoint>,
    rsiPoints: List<RsiPoint>,
    chartHeight: androidx.compose.ui.unit.Dp?,
    watchLevels: List<Double> = emptyList(),
) {
    var touchIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(chartData, selectedPeriod) { touchIndex = null }
    val indicatorsAvailable = selectedPeriod.supportsIndicators()
    val bands = if (indicatorConfig.showBollinger && indicatorsAvailable) bollingerPoints else emptyList()
    val rsi = if (indicatorConfig.showRsi && indicatorsAvailable) rsiPoints else emptyList()

    ClaritySparkChart(
        chartData = chartData,
        isPositive = isPositive,
        lineColor = lineColor,
        selectedPeriod = selectedPeriod,
        smaLevels = smaLevels,
        bollingerPoints = bands,
        watchLevels = watchLevels,
        touchIndex = touchIndex,
        onTouchIndexChange = { touchIndex = it },
        modifier = if (chartHeight != null) {
            Modifier.fillMaxWidth().height(chartHeight)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        },
    )
    if (rsi.isNotEmpty() && (chartData?.timestamps?.size ?: 0) >= 2) {
        ClarityRsiPane(
            timestamps = chartData?.timestamps.orEmpty(),
            rsiPoints = rsi,
            touchIndex = touchIndex,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (chartHeight != null) 64.dp else 72.dp)
                .padding(top = 6.dp),
        )
    }
}

@Composable
internal fun ClarityChartWithIndicators(
    chartData: IntradayChartData?,
    isPositive: Boolean,
    lineColor: Color,
    selectedPeriod: ChartPeriod,
    smaLevels: List<SmaChartLevel>,
    indicatorConfig: ChartIndicatorConfig,
    bollingerPoints: List<BollingerPoint>,
    rsiPoints: List<RsiPoint>,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp? = null,
    watchLevels: List<Double> = emptyList(),
) {
    Column(modifier = modifier) {
        ClarityChartWithIndicatorsBody(
            chartData = chartData,
            isPositive = isPositive,
            lineColor = lineColor,
            selectedPeriod = selectedPeriod,
            smaLevels = smaLevels,
            indicatorConfig = indicatorConfig,
            bollingerPoints = bollingerPoints,
            rsiPoints = rsiPoints,
            chartHeight = chartHeight,
            watchLevels = watchLevels,
        )
    }
}

/** RSI-panel (skala 0–100, hjälplinjer vid 30/70) som delar x-axel med kursgrafen. */
@Composable
private fun ClarityRsiPane(
    timestamps: List<Long>,
    rsiPoints: List<RsiPoint>,
    touchIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val rsiColor = MaterialTheme.colorScheme.tertiary
    val guideColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val textMeasurer = rememberTextMeasurer()
    val aligned = remember(timestamps, rsiPoints) { alignSeriesToChart(timestamps, rsiPoints) { it.timestamp } }

    Canvas(modifier = modifier) {
        val lastIndex = timestamps.lastIndex.coerceAtLeast(1)
        val topPadding = size.height * 0.06f
        val chartHeight = size.height * 0.88f
        fun xFor(index: Int): Float = index * (size.width / lastIndex.toFloat())
        fun yFor(value: Double): Float = topPadding + chartHeight * (1f - (value / 100.0).toFloat())

        // Området mellan 30 och 70 ("neutral zon") tonas svagt, ytterlinjerna ritas streckade.
        drawRect(
            color = rsiColor.copy(alpha = 0.06f),
            topLeft = Offset(0f, yFor(70.0)),
            size = Size(size.width, yFor(30.0) - yFor(70.0)),
        )
        val guideStroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
        )
        listOf(30.0, 70.0).forEach { level ->
            drawLine(
                color = guideColor,
                start = Offset(0f, yFor(level)),
                end = Offset(size.width, yFor(level)),
                strokeWidth = guideStroke.width,
                pathEffect = guideStroke.pathEffect,
            )
        }

        val path = Path()
        var started = false
        aligned.forEachIndexed { i, point ->
            if (point == null) return@forEachIndexed
            val offset = Offset(xFor(i), yFor(point.value))
            if (!started) {
                path.moveTo(offset.x, offset.y)
                started = true
            } else {
                path.lineTo(offset.x, offset.y)
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = rsiColor,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        if (touchIndex != null && touchIndex in timestamps.indices) {
            drawLine(
                color = crosshairColor,
                start = Offset(xFor(touchIndex), 0f),
                end = Offset(xFor(touchIndex), size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        val shownValue = aligned.getOrNull(touchIndex ?: aligned.lastIndex)?.value
            ?: aligned.lastOrNull { it != null }?.value
        val label = "RSI ${TechnicalIndicators.DEFAULT_RSI_PERIOD}" +
            (shownValue?.let { " · ${CurrencyHelper.formatDecimal(it)}" } ?: "")
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            style = TextStyle(fontSize = 9.sp, color = rsiColor, fontWeight = FontWeight.Bold),
            topLeft = Offset(4.dp.toPx(), 0f),
        )
    }
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
    indicatorConfig: ChartIndicatorConfig = ChartIndicatorConfig(),
    bollingerPoints: List<BollingerPoint> = emptyList(),
    rsiPoints: List<RsiPoint> = emptyList(),
    onIndicatorConfigChange: ((ChartIndicatorConfig) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showSettings by remember { mutableStateOf(false) }
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
                        style = NumericStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                    DailyChangePill(periodChange = periodChange, changeColor = changeColor)
                }
            }
            if (onIndicatorConfigChange != null) {
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Välj indikatorer i grafen",
                        tint = colorScheme.onSurfaceVariant,
                    )
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

        ClarityChartWithIndicators(
            chartData = chartData,
            isPositive = isPositive,
            lineColor = changeColor,
            selectedPeriod = selectedPeriod,
            smaLevels = smaLevels,
            indicatorConfig = indicatorConfig,
            bollingerPoints = bollingerPoints,
            rsiPoints = rsiPoints,
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
    if (showSettings && onIndicatorConfigChange != null) {
        ChartSettingsDialog(
            config = indicatorConfig,
            canShowIndicators = selectedPeriod.supportsIndicators(),
            onChange = onIndicatorConfigChange,
            onDismiss = { showSettings = false },
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
        data.returnOnEquity != null ||
        data.priceToBook != null ||
        data.evToEbitda != null ||
        data.debtToEquity != null
    val stats = if (hasMetrics) {
        listOf(
            "P/E" to (data.peRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "P/S" to (data.psRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "Direktavkastning" to (data.dividendYield?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
            "Vinst/aktie" to (data.earningsPerShare?.let { CurrencyHelper.formatPrice(it, data.currency) } ?: "-"),
            "Börsvärde" to (data.marketCap?.let { formatCompactMarketCap(it, data.currency) } ?: "-"),
            "ROE" to (data.returnOnEquity?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
            "P/B" to (data.priceToBook?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "EV/EBITDA" to (data.evToEbitda?.let { CurrencyHelper.formatDecimal(it) } ?: "-"),
            "Skuldsättn." to (data.debtToEquity?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"),
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
                style = NumericStyle.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
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

/**
 * Analytikernas kursmål: spann lågt–högt med nuvarande kurs och snittmål markerade, antal
 * analytiker, uppsida mot kursen och rekommendation som etikett. Visas bara om något finns.
 */
@Composable
private fun ClarityAnalystTargetCard(data: StockDetailData) {
    val mean = data.targetMeanPrice
    val low = data.targetLowPrice
    val high = data.targetHighPrice
    val label = recommendationLabel(data.recommendationKey)
    if (mean == null && low == null && high == null && label == null) return

    val colorScheme = MaterialTheme.colorScheme
    val currency = data.financialCurrency ?: data.currency
    val price = data.lastPrice
    val hasSpan = low != null && high != null && high > low
    fun fractionOf(value: Double?): Float? =
        if (hasSpan && value != null) ((value - low!!) / (high!! - low)).coerceIn(0.0, 1.0).toFloat() else null
    // Kursen kan ligga utanför analytikernas spann — markören klämms då till kanten.
    val priceFraction = fractionOf(price?.takeIf { data.financialCurrency == null || sameCurrency(data.financialCurrency, data.currency) })
    val meanFraction = fractionOf(mean)
    val upside = analystUpsidePercent(mean, price, data.financialCurrency, data.currency)
    val recommendationColor = recommendationColor(data.recommendationKey)
    val meanTickColor = colorScheme.onSurfaceVariant
    val markerColor = colorScheme.primary
    val markerInnerColor = colorScheme.surface

    fun fmt(value: Double?): String = value?.let { CurrencyHelper.formatPrice(it, currency) } ?: "-"
    val analystText = data.analystCount?.let { "Baserat på $it analytiker" }
    val upsideText = upside?.let {
        val sign = if (it >= 0) "+" else ""
        "$sign${CurrencyHelper.formatDecimal(it)} % mot kursen"
    }
    val summary = listOfNotNull(analystText, upsideText).joinToString(" · ")
    val description = buildString {
        append("Analytikernas kursmål")
        if (mean != null) append(", snitt ${fmt(mean)}")
        if (low != null) append(", lägsta ${fmt(low)}")
        if (high != null) append(", högsta ${fmt(high)}")
        if (summary.isNotEmpty()) append(", $summary")
        if (label != null) append(", rekommendation $label")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ANALYTIKERNAS KURSMÅL",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    ),
                    color = LocalTextTertiary.current,
                )
                if (label != null) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .background(recommendationColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = recommendationColor,
                    )
                }
            }
            if (hasSpan) {
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
                    if (meanFraction != null) {
                        val tickX = size.width * meanFraction
                        drawLine(
                            color = meanTickColor,
                            start = Offset(tickX, top - 3.dp.toPx()),
                            end = Offset(tickX, top + trackHeight + 3.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    if (priceFraction != null) {
                        val markerX = size.width * priceFraction
                        drawCircle(color = markerColor, radius = 7.dp.toPx(), center = Offset(markerX, size.height / 2f))
                        drawCircle(color = markerInnerColor, radius = 3.dp.toPx(), center = Offset(markerX, size.height / 2f))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RangeLabel(fmt(low))
                    RangeLabel(fmt(mean), emphasized = true)
                    RangeLabel(fmt(high))
                }
            } else if (mean != null) {
                RangeLabel(fmt(mean), emphasized = true)
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTextTertiary.current,
                )
            }
        }
    }
}

@Composable
private fun recommendationColor(key: String?): Color = when (key?.trim()?.lowercase()) {
    "strong_buy", "buy" -> LocalPriceUp.current
    "underperform", "sell" -> LocalPriceDown.current
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Svensk etikett för Yahoos recommendationKey, eller null om den saknas ("none") eller är okänd. */
internal fun recommendationLabel(key: String?): String? = when (key?.trim()?.lowercase()) {
    "strong_buy" -> "Starkt köp"
    "buy" -> "Köp"
    "hold" -> "Behåll"
    "underperform" -> "Minska"
    "sell" -> "Sälj"
    else -> null
}

private fun sameCurrency(a: String?, b: String?): Boolean =
    a != null && b != null && a.equals(b, ignoreCase = true)

/**
 * Uppsida i procent från [lastPrice] till snittkursmålet. Null om något saknas, om kursen inte
 * är positiv eller om kursmålets valuta ([financialCurrency]) skiljer sig från handelsvalutan.
 * Saknas [financialCurrency] antas valutorna vara lika.
 */
internal fun analystUpsidePercent(
    targetMean: Double?,
    lastPrice: Double?,
    financialCurrency: String?,
    tradingCurrency: String?,
): Double? {
    if (targetMean == null || lastPrice == null || lastPrice <= 0.0) return null
    if (financialCurrency != null && !sameCurrency(financialCurrency, tradingCurrency)) return null
    return (targetMean / lastPrice - 1.0) * 100.0
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

/** Liten rad i hero-kortet, t.ex. "Rapport om 12 dagar · 23 okt". Null om datum saknas eller passerat. */
private fun earningsLabel(earnings: NextEarningsInfo?): String? {
    earnings ?: return null
    return earningsLabel(earnings.reportDateMillis, earnings.isAnnualReport, System.currentTimeMillis())
}

internal fun earningsLabel(reportDateMillis: Long, isAnnualReport: Boolean, nowMillis: Long): String? {
    if (reportDateMillis <= 0L) return null
    fun startOfDay(millis: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val daysLeft = Math.round((startOfDay(reportDateMillis) - startOfDay(nowMillis)) / 86_400_000.0).toInt()
    if (daysLeft < 0) return null
    val noun = if (isAnnualReport) "Bokslut" else "Rapport"
    val whenText = when (daysLeft) {
        0 -> "$noun idag"
        1 -> "$noun i morgon"
        else -> "$noun om $daysLeft dagar"
    }
    val date = SimpleDateFormat("d MMM", Locale("sv", "SE")).format(java.util.Date(reportDateMillis))
    return "$whenText · $date"
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
