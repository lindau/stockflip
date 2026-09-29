package com.stockflip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp
import com.stockflip.ui.theme.NumericSecondaryStyle
import com.stockflip.ui.theme.NumericStyle
import com.stockflip.ui.theme.Space

/**
 * Platt listrad: namn + andrarad till vänster, valfri sparkline, kurs + dagsförändring till höger.
 * Utlöst bevakning markeras med en liten accentprick före namnet — inga badges.
 *
 * @param priceValue råvärdet bakom [price]; används för den tonade blinkningen vid uppdatering.
 * @param changePositive `null` = neutral färg (t.ex. 0,0 %); annars grön/röd. Tecknet finns alltid i [change].
 */
@Composable
fun WatchRow(
    title: String,
    subtitle: String,
    price: String,
    priceValue: Double,
    change: String?,
    changePositive: Boolean?,
    triggered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sparkline: List<Double>? = null,
    showDivider: Boolean = true,
    staleLabel: String? = null,
) {
    val triggeredLabel = "Utlöst"
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Space.screenH),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clickable(onClick = onClick)
                .padding(horizontal = Space.screenH, vertical = Space.md)
                .semantics(mergeDescendants = true) {
                    if (triggered) stateDescription = triggeredLabel
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (triggered) {
                        Box(
                            Modifier
                                .padding(end = Space.sm)
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (staleLabel != null) {
                    Text(
                        text = staleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (sparkline != null) Sparkline(sparkline)
            Column(Modifier.widthIn(min = 76.dp), horizontalAlignment = Alignment.End) {
                PriceText(
                    text = price,
                    value = priceValue,
                    style = NumericStyle,
                    textAlign = TextAlign.End,
                )
                if (change != null) {
                    Text(
                        text = change,
                        style = NumericSecondaryStyle,
                        color = when (changePositive) {
                            true -> LocalPriceUp.current
                            false -> LocalPriceDown.current
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
