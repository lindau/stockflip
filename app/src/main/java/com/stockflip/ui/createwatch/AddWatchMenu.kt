package com.stockflip.ui.createwatch

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stockflip.R

/**
 * Menyn bakom "+" för att skapa en bevakning. Delas av Bevakningar-fliken och aktiesidan så att
 * valen (och deras ordning) är desamma oavsett var man startar. [singleLabel] är texten för det
 * första valet (en enkel bevakning på en aktie).
 */
@Composable
internal fun AddWatchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    singleLabel: String,
    onSingle: () -> Unit,
    onPair: () -> Unit,
    onCombined: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(singleLabel) }, onClick = { onDismiss(); onSingle() })
        DropdownMenuItem(text = { Text(stringResource(R.string.watchlist_aktiepar)) }, onClick = { onDismiss(); onPair() })
        DropdownMenuItem(text = { Text(stringResource(R.string.watchlist_kombinerad)) }, onClick = { onDismiss(); onCombined() })
    }
}
