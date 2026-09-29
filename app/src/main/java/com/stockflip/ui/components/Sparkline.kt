package com.stockflip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Mycket diskret kurskurva utan axlar. Ritar ingenting om serien har färre än två punkter. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
) {
    Canvas(modifier.size(width, height)) {
        val points = sparklinePoints(values, size.width, size.height)
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Skalar [values] till en ruta [w] × [h] (y växer nedåt). Platt serie hamnar mitt i rutan. */
internal fun sparklinePoints(values: List<Double>, w: Float, h: Float): List<Offset> {
    if (values.size < 2) return emptyList()
    val lo = values.min()
    val hi = values.max()
    val span = hi - lo
    val pad = 1f
    return values.mapIndexed { i, v ->
        val x = w * i / (values.size - 1)
        val y = if (span == 0.0) h / 2f else pad + (h - 2 * pad) * (1f - ((v - lo) / span).toFloat())
        Offset(x, y)
    }
}
