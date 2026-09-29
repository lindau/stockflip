package com.stockflip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stockflip.R

// En enda typsnittsfamilj (Inter) i två vikter: Normal/Medium för text, SemiBold enbart för rubriker.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Tabulära siffror — kolumner linjerar och kurser hoppar inte vid uppdatering. */
private const val TNUM = "tnum"

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Double = 0.0,
    tnum: Boolean = false,
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tnum) TNUM else null,
)

// Skala: 11 (etikett) · 13 (sekundär) · 15 (brödtext/rad) · 28 (skärmtitel) · 44 (hero-kurs).
val Typography = Typography(
    displayLarge   = style(FontWeight.Medium, 44, 48, -1.7, tnum = true),
    displayMedium  = style(FontWeight.Medium, 44, 48, -1.7, tnum = true),
    displaySmall   = style(FontWeight.Medium, 28, 32, -0.8, tnum = true),

    headlineLarge  = style(FontWeight.SemiBold, 28, 32, -0.8),
    headlineMedium = style(FontWeight.SemiBold, 28, 32, -0.8),
    headlineSmall  = style(FontWeight.SemiBold, 20, 26, -0.3),

    titleLarge     = style(FontWeight.SemiBold, 20, 26, -0.3),
    titleMedium    = style(FontWeight.Medium, 15, 22, -0.15),
    titleSmall     = style(FontWeight.Medium, 13, 18),

    bodyLarge      = style(FontWeight.Normal, 15, 22),
    bodyMedium     = style(FontWeight.Normal, 13, 18),
    bodySmall      = style(FontWeight.Normal, 13, 18),

    labelLarge     = style(FontWeight.Medium, 15, 20),
    labelMedium    = style(FontWeight.Medium, 13, 18),
    // Små etiketter — gemener/versaler styrs av anroparen, grå via onSurfaceVariant.
    labelSmall     = style(FontWeight.Medium, 11, 16, 0.9),
)

// ─── Numeriska stilar ─────────────────────────────────────────────────────────
val NumericStyle = style(FontWeight.Medium, 15, 20, -0.15, tnum = true)
val NumericSecondaryStyle = style(FontWeight.Normal, 13, 18, tnum = true)
