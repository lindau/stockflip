package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.CountryFlagHelper
import com.stockflip.CurrencyHelper
import com.stockflip.LiveWatchData
import com.stockflip.StockSearchResult
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalTextTertiary
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun ClarityCaseCard(
    item: WatchItem,
    live: LiveWatchData = LiveWatchData(),
    priceFormat: (Double) -> String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val status = clarityCaseStatus(item)
    val isTriggered = item.isTriggered || LocalIsNewTrigger.current
    val isNear = LocalNearTriggerLabel.current != null
    val stripeColor = when {
        isTriggered -> colorScheme.tertiary
        isNear -> colorScheme.primary
        !item.isActive -> colorScheme.outlineVariant
        else -> colorScheme.primary
    }
    val statusBackground = when {
        isTriggered -> colorScheme.tertiary
        isNear -> colorScheme.primaryContainer
        !item.isActive -> colorScheme.outlineVariant
        else -> colorScheme.primaryContainer
    }
    val statusColor = when {
        isTriggered -> colorScheme.onTertiary
        isNear -> colorScheme.onPrimaryContainer
        !item.isActive -> colorScheme.onSurfaceVariant
        else -> colorScheme.onPrimaryContainer
    }
    val cardColor = if (isTriggered) {
        colorScheme.tertiaryContainer.copy(alpha = 0.38f)
    } else {
        containerColor
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTriggered) colorScheme.tertiary.copy(alpha = 0.35f) else LocalCardBorder.current,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(stripeColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            ) {
                Text(
                    text = clarityCaseMeta(item),
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
                    text = clarityCaseTitle(item, priceFormat),
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = clarityCaseSubtitle(item, live, priceFormat),
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = status,
                modifier = Modifier
                    .align(Alignment.Top)
                    .background(statusBackground, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = statusColor,
                maxLines = 1,
            )
        }
    }
}

private fun clarityCaseMeta(item: WatchItem): String {
    val symbol = clarityCaseSymbol(item)
    val name = clarityCaseName(item, symbol)
    val flag = clarityCaseFlag(symbol)
    return listOfNotNull(flag, name, symbol)
        .joinToString(" · ")
        .uppercase(Locale("sv", "SE"))
}

private fun clarityCaseName(item: WatchItem, symbol: String): String {
    return if (item.watchType is WatchType.PricePair) {
        val left = item.companyName1 ?: item.ticker1
        val right = item.companyName2 ?: item.ticker2
        listOfNotNull(left, right).joinToString(" ÷ ").ifBlank { symbol }
    } else {
        item.companyName ?: item.companyName1 ?: symbol
    }
}

private fun clarityCaseTitle(item: WatchItem, priceFormat: (Double) -> String): String {
    val currency = CurrencyHelper.getCurrencyFromSymbol(clarityCaseSymbol(item))
    return when (val watchType = item.watchType) {
        is WatchType.PriceTarget -> "Prismål ${CurrencyHelper.formatPrice(watchType.targetPrice, currency)}"
        is WatchType.KeyMetrics -> {
            val metric = when (watchType.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E"
                WatchType.MetricType.PS_RATIO -> "P/S"
                WatchType.MetricType.DIVIDEND_YIELD -> "Direktavk."
            }
            val direction = when (watchType.direction) {
                WatchType.PriceDirection.ABOVE -> "över"
                WatchType.PriceDirection.BELOW -> "under"
            }
            "$metric $direction ${formatMetricTarget(watchType, priceFormat)}"
        }
        is WatchType.ATHBased -> {
            val reference = when (watchType.reference) {
                WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v-nedgång"
                WatchType.HighReference.ALL_TIME_HIGH -> "ATH-nedgång"
            }
            "$reference ${formatDropTarget(watchType, currency, priceFormat)}"
        }
        is WatchType.PriceRange -> {
            val min = CurrencyHelper.formatPrice(watchType.minPrice, currency)
            val max = CurrencyHelper.formatPrice(watchType.maxPrice, currency)
            "Prisintervall $min-$max"
        }
        is WatchType.DailyMove -> "Dagsrörelse ${formatDailyMoveTarget(watchType, priceFormat)}"
        is WatchType.Combined -> "Kombinerat case"
        is WatchType.PricePair -> "Parcase ${priceFormat(watchType.priceDifference)}"
    }
}

private fun clarityCaseSubtitle(
    item: WatchItem,
    live: LiveWatchData,
    priceFormat: (Double) -> String,
): String {
    return when (val watchType = item.watchType) {
        is WatchType.PriceTarget -> {
            val direction = when (watchType.direction) {
                WatchType.PriceDirection.ABOVE -> "över tröskel"
                WatchType.PriceDirection.BELOW -> "under tröskel"
            }
            "Engångslarm · $direction"
        }
        is WatchType.KeyMetrics -> "Nyckeltal · dagligen"
        is WatchType.ATHBased -> {
            val reference = when (watchType.reference) {
                WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v-topp"
                WatchType.HighReference.ALL_TIME_HIGH -> "högsta pris"
            }
            "Engångslarm · från $reference"
        }
        is WatchType.PriceRange -> "Återkommande · pris inom intervall"
        is WatchType.DailyMove -> {
            val current = live.currentDailyChangePercent
            if (current != null) {
                "Återkommande · ${formatSignedPercent(current, priceFormat)} idag"
            } else {
                "Återkommande · väntar på dagsrörelse"
            }
        }
        is WatchType.Combined -> watchType.expression.getDescription()
        is WatchType.PricePair -> "Parbevakning · aktiespread"
    }
}

@Composable
private fun clarityCaseStatus(item: WatchItem): String {
    val nearLabel = LocalNearTriggerLabel.current
    return when {
        !item.isActive -> "Pausad"
        LocalIsNewTrigger.current -> "Ny"
        item.isTriggered -> triggeredStatusLabel(item.lastTriggeredDate)
        nearLabel != null -> nearLabel
        else -> "Aktiv"
    }
}

private fun triggeredStatusLabel(lastTriggeredDate: String?): String {
    val today = WatchItem.getTodayDateString()
    if (lastTriggeredDate == null || lastTriggeredDate == today) return "Utlöst idag"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(lastTriggeredDate)
        parsed?.let { "Utlöst ${SimpleDateFormat("d MMM", Locale("sv", "SE")).format(it)}" } ?: "Utlöst"
    } catch (_: Exception) {
        "Utlöst"
    }
}

private fun clarityCaseSymbol(item: WatchItem): String {
    if (item.watchType is WatchType.PricePair) {
        return listOfNotNull(item.ticker1, item.ticker2).joinToString(" ÷ ").ifBlank { "N/A" }
    }
    return item.ticker
        ?: (item.watchType as? WatchType.Combined)?.expression?.getSymbols()?.firstOrNull()
        ?: item.ticker1
        ?: "N/A"
}

private fun clarityCaseFlag(symbol: String): String? {
    if (StockSearchResult.isCryptoSymbol(symbol)) return "■"
    val currency = CurrencyHelper.getCurrencyFromSymbol(symbol)
    return CountryFlagHelper
        .getCountryCodeFromCurrency(currency)
        ?.let { CountryFlagHelper.getFlagEmoji(it) }
}

private fun formatMetricTarget(
    watchType: WatchType.KeyMetrics,
    priceFormat: (Double) -> String,
): String {
    return when (watchType.metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat(watchType.targetValue)} %"
        else -> priceFormat(watchType.targetValue)
    }
}

private fun formatDropTarget(
    watchType: WatchType.ATHBased,
    currency: String,
    priceFormat: (Double) -> String,
): String {
    return when (watchType.dropType) {
        WatchType.DropType.PERCENTAGE -> "${priceFormat(watchType.dropValue)} %"
        WatchType.DropType.ABSOLUTE -> CurrencyHelper.formatPrice(watchType.dropValue, currency)
    }
}

private fun formatDailyMoveTarget(
    watchType: WatchType.DailyMove,
    priceFormat: (Double) -> String,
): String {
    val threshold = "${priceFormat(watchType.percentThreshold)} %"
    return when (watchType.direction) {
        WatchType.DailyMoveDirection.UP -> "+$threshold"
        WatchType.DailyMoveDirection.DOWN -> "-$threshold"
        WatchType.DailyMoveDirection.BOTH -> "±$threshold"
    }
}

private fun formatSignedPercent(value: Double, priceFormat: (Double) -> String): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    return "$sign${priceFormat(abs(value))} %"
}
