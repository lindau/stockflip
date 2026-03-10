package com.stockflip.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    // extraSmall — ikoner, indikatorer, kompakta taggar
    extraSmall = RoundedCornerShape(8.dp),
    // small — chips, knappar, listkort (tätare radkänsla)
    small = RoundedCornerShape(14.dp),
    // medium — grouped containers, paneler, detaljvyer
    medium = RoundedCornerShape(20.dp),
    // large — bottom sheets, större modala ytor
    large = RoundedCornerShape(22.dp),
    // extraLarge — fullskärmsytor och hero-element
    extraLarge = RoundedCornerShape(28.dp),
)

// Lokal kortshape för listvyer — mer radlik känsla utan att påverka globalt medium
val ListCardShape = RoundedCornerShape(10.dp)

/**
 * Gruppposition för listkort — ger iOS-liknande grouped list-känsla.
 * Kort i samma ticker-grupp kopplas ihop via corner-behandling och noll vertikalt gap.
 */
enum class GroupPosition { ONLY, FIRST, MIDDLE, LAST }

/** Returnerar rätt corner shape baserat på position i gruppen. */
fun groupShape(position: GroupPosition): Shape = when (position) {
    GroupPosition.ONLY   -> RoundedCornerShape(10.dp)
    GroupPosition.FIRST  -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
    GroupPosition.MIDDLE -> RoundedCornerShape(2.dp)
    GroupPosition.LAST   -> RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
}
