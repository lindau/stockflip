package com.stockflip.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Ytor: 12 dp. Chips och knappar: helt runda ([PillShape]).
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Chips, knappar och segmenterade kontroller. */
val PillShape: Shape = CircleShape

// Behålls tills korten i ui/components/cards ersatts (fas 3–4).
val ListCardShape = RoundedCornerShape(12.dp)

enum class GroupPosition { ONLY, FIRST, MIDDLE, LAST }

fun groupShape(position: GroupPosition): Shape = when (position) {
    GroupPosition.ONLY   -> RoundedCornerShape(12.dp)
    GroupPosition.FIRST  -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
    GroupPosition.MIDDLE -> RoundedCornerShape(2.dp)
    GroupPosition.LAST   -> RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
}
