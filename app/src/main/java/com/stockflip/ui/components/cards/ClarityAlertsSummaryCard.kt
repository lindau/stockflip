package com.stockflip.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ui.theme.LocalCardBorder
import com.stockflip.ui.theme.NordikNumericStyle

@Composable
fun ClarityAlertsSummaryCard(
    triggeredTodayCount: Int,
    activeCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlightCount = if (triggeredTodayCount > 0) triggeredTodayCount else activeCount
    val title = if (triggeredTodayCount > 0) {
        "$triggeredTodayCount utlösta larm"
    } else {
        "$activeCount aktiva larm"
    }
    val label = if (triggeredTodayCount > 0) "Nya aviseringar" else "Aktiva bevakningar"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LocalCardBorder.current),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = if (triggeredTodayCount > 0) {
                            colorScheme.tertiaryContainer
                        } else {
                            colorScheme.primaryContainer
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = highlightCount.toString(),
                    style = NordikNumericStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                    color = if (triggeredTodayCount > 0) colorScheme.onTertiaryContainer else colorScheme.onPrimaryContainer,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "$activeCount aktiva · $totalCount totalt",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
