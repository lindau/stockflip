package com.stockflip.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.ui.theme.Space

/** Höjden på rubrikraden högst upp på flikarna; samma för alla så att rubrikerna hamnar på samma höjd. */
internal val TabHeaderHeight = 48.dp

/** Rubrik för en flik. Raden är lika hög som Bevakningar-radens ikonknappar, så rubriken ligger på samma höjd. */
@Composable
internal fun TabTitle(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(start = Space.screenH, end = Space.screenH, top = Space.md).heightIn(min = TabHeaderHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}
