package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.NordikNumericStyle

@Composable
fun OverviewSummaryCard(
    nearTriggerCount: Int,
    triggeredTodayCount: Int,
    activeCount: Int,
    modifier: Modifier = Modifier,
) {
    val cardBorder = LocalCardBorder.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, cardBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colorScheme.primaryContainer,
                            colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            colorScheme.tertiaryContainer.copy(alpha = 0.24f),
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (activeCount > 0) "Marknadsläge" else "Inga aktiva case ännu",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onPrimaryContainer,
                )

                MetricsStrip(
                    nearTriggerCount = nearTriggerCount,
                    triggeredTodayCount = triggeredTodayCount,
                    activeCount = activeCount,
                )
            }
        }
    }
}

@Composable
private fun MetricsStrip(
    nearTriggerCount: Int,
    triggeredTodayCount: Int,
    activeCount: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.46f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetric(
                value = nearTriggerCount.toString(),
                label = "Nära",
                modifier = Modifier.weight(1f),
            )
            MetricDivider()
            SummaryMetric(
                value = triggeredTodayCount.toString(),
                label = "Utlösta",
                modifier = Modifier.weight(1f),
            )
            MetricDivider()
            SummaryMetric(
                value = activeCount.toString(),
                label = "Aktiva",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
    ) {
        Text(
            text = value,
            style = NordikNumericStyle,
            color = colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun MetricDivider() {
    val colorScheme = MaterialTheme.colorScheme
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(colorScheme.outlineVariant.copy(alpha = 0.72f))
    )
}
