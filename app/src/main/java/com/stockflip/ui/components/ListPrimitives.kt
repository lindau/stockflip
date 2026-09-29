package com.stockflip.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockflip.ui.theme.NumericSecondaryStyle
import com.stockflip.ui.theme.PillShape
import com.stockflip.ui.theme.Space

/** Liten versal rubrik med valfritt antal, t.ex. `UTLÖSTA 2`. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.screenH, end = Space.screenH, top = 22.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Nyckeltalsrad: etikett till vänster (grå), värde till höger. Ersätter små kort. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = Space.screenH, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = NumericSecondaryStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Intervallstapel, t.ex. 52-veckorsintervall. [fraction] är kursens läge 0f..1f
 * (se [rangeFraction]); [low] och [high] visas under stapeln.
 */
@Composable
fun RangeBar(
    label: String,
    low: String,
    high: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val pin = 9.dp
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenH, vertical = Space.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: från $low till $high, kursen ligger på ${(fraction.coerceIn(0f, 1f) * 100).toInt()} procent av intervallet"
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Layout(
            content = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Box(
                    Modifier
                        .size(pin)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.sm),
        ) { measurables, constraints ->
            val track = measurables[0].measure(constraints)
            val dot = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
            val travel = (track.width - dot.width).coerceAtLeast(0)
            val h = dot.height
            layout(track.width, h) {
                track.placeRelative(0, (h - track.height) / 2)
                dot.placeRelative((travel * fraction.coerceIn(0f, 1f)).toInt(), 0)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(low, style = NumericSecondaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(high, style = NumericSecondaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Status som liten pill. [highlighted] = accentfärgad (t.ex. utlöst); annars neutral (väntar). */
@Composable
fun PillStatus(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        color = fg,
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = Space.sm, vertical = 2.dp),
    )
}

/** Kort tomt tillstånd: en rad text och en (valfri) handling. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenH, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Skeleton-rad som ersätter spinner vid laddning. Pulserar mjukt mellan två alfavärden. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val a by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(a)
            .padding(horizontal = Space.screenH, vertical = Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(120.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
            Box(Modifier.width(180.dp).height(11.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(56.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
            Box(Modifier.width(40.dp).height(11.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
        }
    }
}

/**
 * Visar ett ångra-meddelande efter t.ex. radering (ersätter bekräftelsedialog).
 * Returnerar `true` om användaren tryckte Ångra.
 */
suspend fun SnackbarHostState.showUndo(message: String, actionLabel: String = "Ångra"): Boolean =
    showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Short) ==
        SnackbarResult.ActionPerformed
