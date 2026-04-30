package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ChartPeriod
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.IntradayChartData
import com.stockflip.StockDetailData
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.LocalTextTertiary
import com.stockflip.ui.theme.NordikNumericStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun ClarityStockDetailPanel(
    data: StockDetailData,
    chartData: IntradayChartData?,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
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
) {
    val colorScheme = MaterialTheme.colorScheme
    val changePercent = dailyChangePercent(data)
    val changeColor = when {
        changePercent == null -> colorScheme.onSurfaceVariant
        changePercent >= 0.0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val isPositive = changePercent == null || changePercent >= 0.0

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
                Text(
                    text = changeText(data, changePercent),
                    modifier = Modifier
                        .background(changeColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = changeColor,
                    maxLines = 1,
                )
            }

            ClaritySparkChart(
                chartData = chartData,
                isPositive = isPositive,
                lineColor = changeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .padding(top = 16.dp),
            )

            ClarityPeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ClaritySparkChart(
    chartData: IntradayChartData?,
    isPositive: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val prices = chartData?.prices.orEmpty()
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
    Canvas(modifier = modifier) {
        val minPrice = prices.min()
        val maxPrice = prices.max()
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
    }
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colorScheme.onSurface else Color.Transparent)
                    .clickable { onPeriodSelected(period) }
                    .padding(vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) colorScheme.surface else colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
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

private fun dailyChangePercent(data: StockDetailData): Double? {
    data.dailyChangePercent?.let { return it }
    val lastPrice = data.lastPrice
    val previousClose = data.previousClose
    return if (lastPrice != null && previousClose != null && previousClose > 0.0) {
        ((lastPrice - previousClose) / previousClose) * 100.0
    } else {
        null
    }
}

private fun changeText(data: StockDetailData, changePercent: Double?): String {
    if (changePercent == null) return "— %"
    val delta = if (data.lastPrice != null && data.previousClose != null) {
        data.lastPrice - data.previousClose
    } else {
        null
    }
    val sign = when {
        changePercent > 0.0 -> "+"
        changePercent < 0.0 -> "−"
        else -> ""
    }
    val percentText = "$sign${CurrencyHelper.formatDecimal(abs(changePercent))} %"
    val deltaText = delta?.let {
        "$sign${CurrencyHelper.formatDecimal(abs(it))}"
    }
    return listOfNotNull(deltaText, percentText).joinToString("  ")
}
