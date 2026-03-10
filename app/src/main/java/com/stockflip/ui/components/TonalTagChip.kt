package com.stockflip.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tagg-chip i Nordisk Precision-stil.
 *
 * Två visuella tillstånd:
 * - Omarkerad: [secondaryContainer]-bakgrund (Chip-token), [outlineVariant]-border — neutral
 * - Markerad: [primaryContainer]-bakgrund (ChipSelected-token), ingen border — distinkt men lugn
 *
 * Typografi: [labelMedium] med 0.6 sp letter-spacing — passar ticker-koder och korta etiketter.
 * Parametern [isSelected] har default false så befintliga anropssajter inte behöver ändras.
 */
@Composable
fun TonalTagChip(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val borderColor = if (isSelected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    AssistChip(
        onClick = { },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = borderColor,
        ),
        modifier = modifier,
    )
}
