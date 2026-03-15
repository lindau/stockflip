package com.stockflip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stockflip.R

// ─── Custom Font Families ──────────────────────────────────────────────────────
// DM Serif Display — används för rubriker och display-text (bolagsnamn, skärmtitlar)
val DmSerifDisplay = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal)
)

// JetBrains Mono — används för numeriska värden (priser, procenttal, nyckeltal)
// Har inbyggda tabular figures (fast bredd per siffra) utan fontFeatureSettings
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold)
)

val Typography = Typography(
    // ── Display ─────────────────────────────────────────────────────────────
    // Används ej i appen idag — behålls för M3-kompatibilitet
    displayLarge = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 57.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 45.sp,
        lineHeight    = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 36.sp,
        lineHeight    = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline ─────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily    = DmSerifDisplay,
        fontWeight    = FontWeight.Normal,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title ─────────────────────────────────────────────────────────────────
    // titleLarge — skärmrubriker och sektionstitlar
    titleLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 26.sp,
        letterSpacing = (-0.15).sp,
    ),
    // titleMedium — primär etikett i kortet (bolagsnamn, primärdata)
    titleMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.sp,
    ),
    // titleSmall — sekundär etikett, grupprubriker i list rows
    titleSmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.sp,
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    // bodyLarge — standard brödtext, dialogtext
    bodyLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    // bodyMedium — kompakt brödtext, kortbeskrivningar
    bodyMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    // bodySmall — minsta brödtext, tidsstämplar
    bodySmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.2.sp,
    ),

    // ── Label ─────────────────────────────────────────────────────────────────
    // labelLarge — knappetikett, active filter label
    labelLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // labelMedium — ticker-koder, chips, kategorietiketter; bredare spacing för läsbarhet
    labelMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    // labelSmall — badge-text, minsta metadata
    labelSmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ─── Numerisk datastil ────────────────────────────────────────────────────────
// Används för priser, procenttal, nyckeltal — precis, tät, läsbar utan att kännas terminal.
// JetBrains Mono har inbyggda tabular figures (fast bredd per siffra).
val NordikNumericStyle = TextStyle(
    fontFamily    = JetBrainsMono,
    fontWeight    = FontWeight.SemiBold,
    fontSize      = 15.sp,
    lineHeight    = 20.sp,
    letterSpacing = (-0.15).sp,
)

// Kompakt variant för sekundära siffror (t.ex. daglig förändring, volym, spread)
val NordikNumericSecondaryStyle = TextStyle(
    fontFamily    = JetBrainsMono,
    fontWeight    = FontWeight.Normal,
    fontSize      = 12.sp,
    lineHeight    = 16.sp,
    letterSpacing = (-0.1).sp,
)
