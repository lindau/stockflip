package com.stockflip.ui.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.NordikNumericStyle
import com.stockflip.ui.theme.groupShape
import kotlin.math.abs

@Composable
fun PairCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    groupPosition: GroupPosition = GroupPosition.ONLY,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val pricePair = item.watchType as? WatchType.PricePair ?: return

    val currency1 = CurrencyHelper.getCurrencyFromSymbol(item.ticker1)
    val currency2 = CurrencyHelper.getCurrencyFromSymbol(item.ticker2)

    val isTriggered = item.currentPrice1 > 0.0 && item.currentPrice2 > 0.0 && run {
        val diff = abs(item.currentPrice1 - item.currentPrice2)
        (pricePair.notifyWhenEqual && diff < 0.01) ||
            (pricePair.priceDifference > 0 && diff <= pricePair.priceDifference)
    }

    val conditionText = buildString {
        val hasEqual = pricePair.notifyWhenEqual
        val hasDiff  = pricePair.priceDifference > 0
        if (hasEqual) append("=")
        if (hasEqual && hasDiff) append(" & ")
        if (hasDiff) append("≤ ${priceFormat(pricePair.priceDifference)}")
        if (!hasEqual && !hasDiff) append("=")
    }

    val currentSpread: Double? = if (item.currentPrice1 > 0.0 && item.currentPrice2 > 0.0)
        abs(item.currentPrice1 - item.currentPrice2) else null

    val cardBorder = LocalCardBorder.current

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer else containerColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "containerColor"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor,
        ),
        shape = groupShape(groupPosition),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = animatedBorderColor,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusStripe(isTriggered = isTriggered)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
            ) {
                if (showControls && onToggleActive != null) {
                    // Detaljvy: kompakt titelrad med toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.ticker1 ?: "—"} / ${item.ticker2 ?: "—"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = conditionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTriggered) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier = Modifier
                                .scale(0.7f)
                                .offset(y = (-2).dp),
                        )
                    }
                } else {
                    // Listvy: full hierarki med priser och spread
                    StockPriceRow(
                        companyName = item.companyName1,
                        ticker = item.ticker1,
                        price = if (item.currentPrice1 > 0.0)
                            CurrencyHelper.formatPrice(item.currentPrice1, currency1) else "—",
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    StockPriceRow(
                        companyName = item.companyName2,
                        ticker = item.ticker2,
                        price = if (item.currentPrice2 > 0.0)
                            CurrencyHelper.formatPrice(item.currentPrice2, currency2) else "—",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Spread + villkorsrad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text(
                                text = "Skillnad nu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (currentSpread != null) priceFormat(currentSpread) else "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTriggered) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Villkor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = conditionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTriggered) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TriggerHistoryRow(triggerHistory)

                if (item.isTriggered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TriggeredBadge()
                }
            }
        }
    }
}

/** Intern hjälpkomposabel för en aktiestock-rad i PairCard. */
@Composable
private fun StockPriceRow(
    companyName: String?,
    ticker: String?,
    price: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = companyName ?: ticker ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (ticker != null) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = price,
            style = NordikNumericStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
