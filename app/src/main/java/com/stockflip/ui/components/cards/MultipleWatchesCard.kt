package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.ui.components.StockSummaryRow
import com.stockflip.ui.theme.ListCardShape
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NordikNumericSecondaryStyle
import com.stockflip.ui.theme.NordikNumericStyle
import kotlin.math.abs

enum class MultipleWatchesPresentation {
    Default,
    Clarity,
}

/**
 * Kort som visar en aktie med flera bevakningar — används på Aktier-tabben.
 * Klick navigerar till StockDetailFragment.
 *
 * Triggered state baseras på [triggeredCount] > 0. Container-ton och räknare
 * använder tertiary (amber) — semantiskt korrekt, inte alarmistisk.
 */
@Composable
fun MultipleWatchesCard(
    symbol: String,
    companyName: String?,
    watchCount: Int,
    triggeredCount: Int = 0,
    currentPrice: Double,
    dailyChangePercent: Double? = null,
    priceFormat: (Double) -> String,
    presentation: MultipleWatchesPresentation = MultipleWatchesPresentation.Default,
    modifier: Modifier = Modifier,
) {
    val isTriggered = triggeredCount > 0
    val cardBorder = LocalCardBorder.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
        shape = if (presentation == MultipleWatchesPresentation.Clarity) RoundedCornerShape(22.dp) else ListCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
        ),
    ) {
        if (presentation == MultipleWatchesPresentation.Clarity) {
            MultipleWatchesClarityContent(
                symbol = symbol,
                companyName = companyName,
                watchCount = watchCount,
                triggeredCount = triggeredCount,
                currentPrice = currentPrice,
                dailyChangePercent = dailyChangePercent,
            )
            return@Card
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            val currency = CurrencyHelper.getCurrencyFromSymbol(symbol)
            StockSummaryRow(
                companyName = companyName,
                ticker = symbol,
                price = currentPrice,
                dailyChangePercent = dailyChangePercent,
                currency = currency,
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "$watchCount bevakningar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (triggeredCount > 0) {
                    Text(
                        text = " · $triggeredCount nådd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultipleWatchesClarityContent(
    symbol: String,
    companyName: String?,
    watchCount: Int,
    triggeredCount: Int,
    currentPrice: Double,
    dailyChangePercent: Double?,
) {
    val currency = CurrencyHelper.getCurrencyFromSymbol(symbol)
    val changeColor = when {
        dailyChangePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        dailyChangePercent >= 0.0 -> LocalPriceUp.current
        else -> LocalPriceDown.current
    }
    val flag = CountryFlagHelper
        .getCountryCodeFromCurrency(currency)
        ?.let { CountryFlagHelper.getFlagEmoji(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = flag ?: symbol.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = companyName ?: symbol,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                )
            }

            MiniSparkline(
                isPositive = dailyChangePercent == null || dailyChangePercent >= 0.0,
                color = changeColor,
                modifier = Modifier
                    .width(58.dp)
                    .height(28.dp),
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (currentPrice > 0.0) CurrencyHelper.formatPrice(currentPrice, currency) else "—",
                    style = NordikNumericStyle.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dailyChangePercent?.let(::formatClarityDailyChangePercent) ?: "— %",
                    style = NordikNumericSecondaryStyle.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = changeColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(changeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "$watchCount bevakningar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (triggeredCount > 0) {
                Text(
                    text = " · $triggeredCount nådd",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun formatClarityDailyChangePercent(value: Double): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    return "$sign${CurrencyHelper.formatDecimal(abs(value))} %"
}

@Composable
private fun MiniSparkline(
    isPositive: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val values = if (isPositive) {
        listOf(0.72f, 0.62f, 0.66f, 0.54f, 0.50f, 0.38f, 0.30f)
    } else {
        listOf(0.30f, 0.40f, 0.38f, 0.50f, 0.58f, 0.62f, 0.72f)
    }
    Canvas(modifier = modifier) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.lastIndex == 0) 0f else size.width * index / values.lastIndex
            val y = size.height * value
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
