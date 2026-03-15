package com.stockflip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Vertikal statusindikator på kortets vänsterkant.
 *
 * Alltid renderad med fast bredd (5 dp) för att förhindra layouthopp när triggered-tillståndet
 * ändras. Färgen går från transparent (inaktiv) till [MaterialTheme.colorScheme.tertiary]
 * (amber/warning) när bevakningen är utlöst — lågmält men distinkt.
 *
 * Intentionellt: använder tertiary (amber) snarare än error (röd) eftersom ett triggat
 * larm inte indikerar ett fel, utan att bevakningsvillkoret är uppfyllt.
 */
@Composable
fun StatusStripe(
    isTriggered: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isTriggered) MaterialTheme.colorScheme.tertiary else Color.Transparent,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "statusStripeColor"
    )

    Box(
        modifier = modifier
            .width(5.dp)
            .fillMaxHeight()
            .background(color = animatedColor)
    )
}
