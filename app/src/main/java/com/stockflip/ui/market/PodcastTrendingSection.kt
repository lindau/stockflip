package com.stockflip.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stockflip.PodcastSignalDto
import com.stockflip.ui.components.PodcastFlagChips
import com.stockflip.ui.components.summaryText
import com.stockflip.ui.theme.Space
import java.util.Locale

/** Topplista över det som diskuteras mest i poddarna just nu (filtrerad på vald marknad i Marknad-fliken). */
internal data class PodcastTrendingUi(
    val items: List<PodcastSignalDto>,
    val onOpen: (PodcastSignalDto) -> Unit,
)

@Composable
internal fun PodcastTrendingContent(ui: PodcastTrendingUi) {
    Column(Modifier.fillMaxWidth()) {
        ui.items.forEachIndexed { index, signal ->
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = Space.screenH), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.fillMaxWidth().clickable { ui.onOpen(signal) }.padding(horizontal = Space.screenH, vertical = Space.md)) {
                Text(signal.name ?: signal.ticker.orEmpty(), style = MaterialTheme.typography.titleMedium)
                val pct = signal.price?.pct?.let { " · " + String.format(Locale("sv", "SE"), "%+.1f %%", it) }.orEmpty()
                Text(
                    "${signal.ticker.orEmpty()} · ${signal.summaryText()}$pct",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PodcastFlagChips(signal, Modifier.padding(top = Space.xs), max = 2)
            }
        }
    }
}
