package com.stockflip.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.SegmentedControl
import com.stockflip.ui.theme.Space

/**
 * Inställningar: tema, data (export/import) och om appen (uppdatering, hjälp, ändringslogg).
 * Tillståndslös; värden äger dialoger, filväljare och nätverksanrop.
 */
@Composable
internal fun SettingsScreen(
    themeMode: ThemeMode,
    versionName: String,
    busy: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenChangelog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Inställningar",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Space.screenH, end = Space.screenH, top = Space.md),
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.sm))

        SectionLabel("Tema")
        SegmentedControl(
            options = ThemeMode.entries.map { it.label },
            selectedIndex = themeMode.ordinal,
            onSelect = { onThemeChange(ThemeMode.entries[it]) },
            modifier = Modifier.padding(horizontal = Space.screenH),
        )

        SectionLabel("Data")
        SettingsRow("Exportera bevakningar", "Spara en säkerhetskopia som fil", onExport, showDivider = false)
        SettingsRow("Importera bevakningar", "Läs in en tidigare säkerhetskopia", onImport)

        SectionLabel("Om StockFlip")
        SettingsRow("Kolla efter uppdatering", null, onCheckUpdate, showDivider = false)
        SettingsRow("Hjälp", "Användarhandbok", onOpenHelp)
        SettingsRow("Version $versionName", "Visa ändringslogg", onOpenChangelog)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Space.screenH),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clickable(onClick = onClick)
                .padding(horizontal = Space.screenH, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
