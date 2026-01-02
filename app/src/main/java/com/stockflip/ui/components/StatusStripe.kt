package com.stockflip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusStripe(
    isTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTriggered) {
        Box(
            modifier = modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        )
    } else {
        Box(modifier = modifier.width(4.dp))
    }
}

