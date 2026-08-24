package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.stockflip.hasPendingNextTradingDayGuard
import com.stockflip.triggerConditionText
import com.stockflip.ui.components.CompanyLogoAvatar
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
    onToggleActive: (() -> Unit)? = null,
    onReactivate: (() -> Unit)? = null,
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
    val canReactivateOneTimeAlert = item.isTriggered &&
        (item.watchType is WatchType.PriceTarget || item.watchType is WatchType.ATHBased)

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

            CompanyLogoAvatar(
                symbol = clarityCaseSymbol(item),
                modifier = Modifier.align(Alignment.CenterVertically),
                size = 44.dp,
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

            Column(
                modifier = Modifier.align(Alignment.Top),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = status,
                    modifier = Modifier
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
                if (canReactivateOneTimeAlert && onReactivate != null) {
                    TextButton(
                        onClick = onReactivate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .semantics { contentDescription = "Återaktivera bevakning" },
                    ) {
                        Text(
                            text = "Återaktivera",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                        )
                    }
                } else if (onToggleActive != null) {
                    Switch(
                        checked = item.isActive,
                        onCheckedChange = { onToggleActive() },
                        colors = watchItemSwitchColors(),
                        thumbContent = { watchItemSwitchThumb() },
                        modifier = Modifier
                            .scale(0.72f)
                            .semantics {
                                contentDescription = if (item.isActive) {
                                    "Inaktivera bevakning"
                                } else {
                                    "Aktivera bevakning"
                                }
                            },
                    )
                }
            }
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
    return item.triggerConditionText(currency = currency, decimalFormat = priceFormat)
}

private fun clarityCaseSubtitle(
    item: WatchItem,
    live: LiveWatchData,
    priceFormat: (Double) -> String,
): String {
    if (item.hasPendingNextTradingDayGuard()) {
        return "Aktiv igen · väntar till nästa handelsdag"
    }
    return when (val watchType = item.watchType) {
        is WatchType.PriceTarget -> "Engångslarm"
        is WatchType.KeyMetrics -> "Nyckeltal · dagligen"
        is WatchType.ATHBased -> {
            val reference = when (watchType.reference) {
                WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v-topp"
                WatchType.HighReference.ALL_TIME_HIGH -> "högsta pris"
            }
            "Engångslarm · från $reference"
        }
        is WatchType.PriceRange -> "Äldre bevakningstyp · prisintervall"
        is WatchType.DailyMove -> {
            val current = live.currentDailyChangePercent
            if (current != null) {
                "Återkommande · ${formatSignedPercent(current, priceFormat)} idag"
            } else {
                "Återkommande · väntar på dagsrörelse"
            }
        }
        is WatchType.InsiderBuy -> "Kontrolleras var 6:e timme"
        is WatchType.Combined -> "Äldre bevakningstyp · kombinerat villkor"
        is WatchType.PricePair -> "Trigger när spreaden når nivån oavsett riktning"
    }
}

@Composable
private fun clarityCaseStatus(item: WatchItem): String {
    val nearLabel = LocalNearTriggerLabel.current
    return when {
        !item.isActive -> "Pausad"
        LocalIsNewTrigger.current -> "Ny"
        item.isTriggered -> triggeredStatusLabel(item.lastTriggeredDate)
        item.hasPendingNextTradingDayGuard() -> "Nästa handelsdag"
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
    return CountryFlagHelper
        .getCountryCodeFromSymbol(symbol)
        ?.let { CountryFlagHelper.getFlagEmoji(it) }
}

private fun formatSignedPercent(value: Double, priceFormat: (Double) -> String): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    return "$sign${priceFormat(abs(value))} %"
}
