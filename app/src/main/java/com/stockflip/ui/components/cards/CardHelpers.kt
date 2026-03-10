package com.stockflip.ui.components.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.ui.theme.LocalOnTriggeredBadge
import com.stockflip.ui.theme.LocalTriggeredBadge

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
