package com.stockflip.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stockflip.ui.theme.NordikNumericStyle

/**
 * Kompakt rad med etikett och numeriskt värde — används i PairCard för varje aktie.
 *
 * Hierarki: [title] är en sekundär identifieringsetikett (bodySmall, onSurfaceVariant),
 * [value] är primärdata (NordikNumericStyle, onSurface).
 */
@Composable
fun MetricRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = NordikNumericStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
