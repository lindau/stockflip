package com.stockflip.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.stockflip.ui.theme.LocalPriceDown
import com.stockflip.ui.theme.LocalPriceUp

/**
 * Visar en formaterad kurs. När [value] ändras tonas en dämpad bakgrund (grön vid uppgång,
 * röd vid nedgång) ut under 600 ms. Siffrorna rullar inte. Första kompositionen blinkar inte.
 */
@Composable
fun PriceText(
    text: String,
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
) {
    val up = LocalPriceUp.current
    val down = LocalPriceDown.current
    var previous by remember { mutableStateOf(value) }
    var tint by remember { mutableStateOf(Color.Transparent) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(value) {
        if (value != previous) {
            tint = if (value > previous) up else down
            previous = value
            flash.snapTo(1f)
            flash.animateTo(0f, tween(durationMillis = 600))
        }
    }

    Text(
        text = text,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier.drawBehind {
            val a = flash.value * FLASH_ALPHA
            if (a > 0f) drawRect(tint.copy(alpha = a))
        },
    )
}

private const val FLASH_ALPHA = 0.18f
