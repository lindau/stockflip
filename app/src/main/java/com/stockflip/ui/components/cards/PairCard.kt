package com.stockflip.ui.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.CurrencyHelper
import com.stockflip.LiveWatchData
import com.stockflip.WatchItem
import com.stockflip.WatchItemUiState
import com.stockflip.WatchType
import com.stockflip.isTriggeredForDisplay
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.JetBrainsMono
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NordikNumericStyle
import com.stockflip.ui.theme.groupShape
import kotlin.math.abs

enum class PairCardPresentation {
    Default,
    Clarity,
}

@Composable
fun PairCard(
    item: WatchItem,
    live: LiveWatchData = LiveWatchData(),
    priceFormat: (Double) -> String,
    groupPosition: GroupPosition = GroupPosition.ONLY,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    presentation: PairCardPresentation = PairCardPresentation.Default,
    modifier: Modifier = Modifier,
) {
    val pricePair = item.watchType as? WatchType.PricePair ?: return

    val currency1 = CurrencyHelper.getCurrencyFromSymbol(item.ticker1)
    val currency2 = CurrencyHelper.getCurrencyFromSymbol(item.ticker2)

    val isTriggered = WatchItemUiState(item, live).isTriggeredForDisplay()

    val conditionText = buildString {
        val hasEqual = pricePair.notifyWhenEqual
        val hasDiff  = pricePair.priceDifference > 0
        if (hasEqual) append("=")
        if (hasEqual && hasDiff) append(" & ")
        if (hasDiff) append("≤ ${priceFormat(pricePair.priceDifference)}")
        if (!hasEqual && !hasDiff) append("=")
    }

    val currentSpread: Double? = if (live.currentPrice1 > 0.0 && live.currentPrice2 > 0.0)
        abs(live.currentPrice1 - live.currentPrice2) else null

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
        shape = if (presentation == PairCardPresentation.Clarity && !showControls) {
            RoundedCornerShape(22.dp)
        } else {
            groupShape(groupPosition)
        },
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
                    .padding(if (presentation == PairCardPresentation.Clarity && !showControls) 18.dp else 12.dp),
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
                            colors = watchItemSwitchColors(),
                            thumbContent = { watchItemSwitchThumb() },
                            modifier = Modifier
                                .scale(0.7f)
                                .offset(y = (-2).dp),
                        )
                    }
                } else if (presentation == PairCardPresentation.Clarity) {
                    PairClarityListContent(
                        item = item,
                        live = live,
                        pricePair = pricePair,
                        priceFormat = priceFormat,
                        conditionText = conditionText,
                        currentSpread = currentSpread,
                        isTriggered = isTriggered,
                        onToggleActive = onToggleActive,
                    )
                } else {
                    // Listvy: full hierarki med priser och spread
                    StockPriceRow(
                        companyName = item.companyName1,
                        ticker = item.ticker1,
                        price = if (live.currentPrice1 > 0.0)
                            CurrencyHelper.formatPrice(live.currentPrice1, currency1) else "—",
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    StockPriceRow(
                        companyName = item.companyName2,
                        ticker = item.ticker2,
                        price = if (live.currentPrice2 > 0.0)
                            CurrencyHelper.formatPrice(live.currentPrice2, currency2) else "—",
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

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
                    TriggeredBadge(item.lastTriggeredDate)
                }
            }
        }
    }
}

@Composable
private fun PairClarityListContent(
    item: WatchItem,
    live: LiveWatchData,
    pricePair: WatchType.PricePair,
    priceFormat: (Double) -> String,
    conditionText: String,
    currentSpread: Double?,
    isTriggered: Boolean,
    onToggleActive: (() -> Unit)?,
) {
    val signalColor = pairSignalColor(
        isTriggered = isTriggered,
        currentSpread = currentSpread,
        targetSpread = pricePair.priceDifference,
    )
    val pairName = "${item.companyName1 ?: item.ticker1 ?: "—"} ÷ ${item.companyName2 ?: item.ticker2 ?: "—"}"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pairName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PairClarityBadge(
                text = if (isTriggered) "Utlöst" else conditionText,
                color = signalColor,
            )
            if (onToggleActive != null) {
                Switch(
                    checked = item.isActive,
                    onCheckedChange = { onToggleActive() },
                    colors = watchItemSwitchColors(),
                    thumbContent = { watchItemSwitchThumb() },
                    modifier = Modifier.scale(0.72f),
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = if (currentSpread != null) priceFormat(currentSpread) else "—",
                style = NordikNumericStyle.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Skillnad nu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        PairClaritySignalLine(
            isTriggered = isTriggered,
            currentSpread = currentSpread,
            targetSpread = pricePair.priceDifference,
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
        )
    }

    if (live.currentPrice1 > 0.0 || live.currentPrice2 > 0.0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PairClarityPriceMeta(
                label = item.ticker1 ?: "—",
                value = if (live.currentPrice1 > 0.0) CurrencyHelper.formatDecimal(live.currentPrice1) else "—",
                modifier = Modifier.weight(1f),
            )
            PairClarityPriceMeta(
                label = item.ticker2 ?: "—",
                value = if (live.currentPrice2 > 0.0) CurrencyHelper.formatDecimal(live.currentPrice2) else "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PairClarityBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun PairClarityPriceMeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PairClaritySignalLine(
    isTriggered: Boolean,
    currentSpread: Double?,
    targetSpread: Double,
    modifier: Modifier = Modifier,
) {
    val color = pairSignalColor(
        isTriggered = isTriggered,
        currentSpread = currentSpread,
        targetSpread = targetSpread,
    )
    val values = when {
        currentSpread == null -> listOf(0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f)
        isTriggered -> listOf(0.66f, 0.60f, 0.62f, 0.54f, 0.46f, 0.42f, 0.34f)
        targetSpread > 0.0 && currentSpread <= targetSpread -> listOf(0.64f, 0.58f, 0.60f, 0.50f, 0.45f, 0.38f, 0.32f)
        else -> listOf(0.36f, 0.42f, 0.40f, 0.48f, 0.54f, 0.58f, 0.66f)
    }

    Canvas(modifier = modifier.width(90.dp)) {
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
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun pairSignalColor(
    isTriggered: Boolean,
    currentSpread: Double?,
    targetSpread: Double,
): Color {
    return when {
        isTriggered -> MaterialTheme.colorScheme.tertiary
        currentSpread == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        targetSpread > 0.0 && currentSpread <= targetSpread -> LocalPriceUp.current
        targetSpread > 0.0 -> LocalPriceDown.current
        else -> MaterialTheme.colorScheme.primary
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
