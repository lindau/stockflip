package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.ui.components.StatusStripe
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.groupShape

@Composable
fun CombinedAlertCard(
    item: WatchItem,
    priceFormat: (Double) -> String,
    groupPosition: GroupPosition = GroupPosition.ONLY,
    showPrice: Boolean = true,
    showControls: Boolean = false,
    onToggleActive: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val combined = item.watchType as? WatchType.Combined ?: return

    val symbols = combined.expression.getSymbols()
    val firstSymbol = symbols.firstOrNull() ?: item.ticker ?: "N/A"

    // Expressionsuttvärdeing kräver alla aktiepriser — utförs av WorkManager.
    // Visuell triggered-markering hämtas från item.isTriggered (DB-tillstånd).
    val isTriggered = item.isTriggered

    val cardBorder = LocalCardBorder.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isTriggered) MaterialTheme.colorScheme.tertiaryContainer else containerColor,
        ),
        shape = groupShape(groupPosition),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isTriggered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else cardBorder,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusStripe(isTriggered = isTriggered)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.companyName ?: firstSymbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = firstSymbol,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = combined.expression.getDescription(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showControls && onToggleActive != null) {
                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier = Modifier
                                .scale(0.7f)
                                .align(Alignment.Top)
                                .offset(y = (-12).dp),
                        )
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
