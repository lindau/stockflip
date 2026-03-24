package com.stockflip.ui.components.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.ui.theme.LocalOnTriggeredBadge
import com.stockflip.ui.theme.LocalTriggeredBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hjälpfunktioner för kortkomponenter.
 */
internal fun formatAlertStatus(watchItem: WatchItem): String {
    return when {
        !watchItem.isActive -> "Status: Inaktiverad"
        watchItem.isTriggered -> "Status: Triggad (${watchItem.lastTriggeredDate ?: "idag"})"
        else -> "Status: Aktiv"
    }
}

/**
 * Kompakt textrad med de senaste utlösningstidpunkterna för en bevakning.
 * Visas bara om listan inte är tom.
 */
@Composable
internal fun TriggerHistoryRow(timestamps: List<Long>) {
    if (timestamps.isEmpty()) return
    val format = remember { SimpleDateFormat("d MMM", Locale("sv", "SE")) }
    val dateStr = remember(timestamps) {
        timestamps.take(5).joinToString(" · ") { format.format(Date(it)) }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Utlöst: $dateStr",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Visar senaste uppdateringstid eller felindikator per kort.
 */
@Composable
internal fun LastUpdatedRow(lastUpdatedAt: Long, updateFailed: Boolean) {
    if (lastUpdatedAt == 0L && !updateFailed) return
    Spacer(modifier = Modifier.height(4.dp))
    val text: String
    val color: androidx.compose.ui.graphics.Color
    if (updateFailed) {
        text = "Misslyckades att uppdatera"
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    } else {
        val timeStr = remember(lastUpdatedAt) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdatedAt))
        }
        text = "Uppdaterad $timeStr"
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * Badge som visas när en bevakning har utlösts idag.
 *
 * Färger hämtas från [LocalTriggeredBadge] och [LocalOnTriggeredBadge] — amber-ton i
 * båda teman. Kontrast verifierad: dark 11.7:1, light 6.8:1 (WCAG AA ✓).
 */
@Composable
internal fun TriggeredBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = LocalTriggeredBadge.current,
    ) {
        Text(
            text = "Utlöst idag",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = LocalOnTriggeredBadge.current,
        )
    }
}
