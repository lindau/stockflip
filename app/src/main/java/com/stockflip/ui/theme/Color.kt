package com.stockflip.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── StockFlip "lugnt verktyg" — Dark ────────────────────────────────────────
// Nästan monokrom yta, en accentfärg. Grön/röd används enbart för kursförändring.

val SF_Dark_Background    = Color(0xFF131519)
val SF_Dark_Surface       = Color(0xFF1B1E24)
val SF_Dark_SurfaceHigh   = Color(0xFF22262D)
val SF_Dark_Line          = Color(0xFF262A31)
val SF_Dark_TextPrimary   = Color(0xFFECEDF0)
val SF_Dark_TextSecondary = Color(0xFF8E94A1)
val SF_Dark_Accent        = Color(0xFF8E9BF0)
val SF_Dark_OnAccent      = Color(0xFF0F1224)
val SF_Dark_AccentSoft    = Color(0xFF23283F)
val SF_Dark_Up            = Color(0xFF4FBF8A)
val SF_Dark_Down          = Color(0xFFE07A7A)

// ─── StockFlip "lugnt verktyg" — Light ───────────────────────────────────────

val SF_Light_Background    = Color(0xFFFAFAFB)
val SF_Light_Surface       = Color(0xFFF1F2F5)
val SF_Light_SurfaceHigh   = Color(0xFFE9EBF0)
val SF_Light_Line          = Color(0xFFE4E5EA)
val SF_Light_TextPrimary   = Color(0xFF14161C)
val SF_Light_TextSecondary = Color(0xFF6A6F7C)
val SF_Light_Accent        = Color(0xFF3D4FBF)
val SF_Light_OnAccent      = Color(0xFFFFFFFF)
val SF_Light_AccentSoft    = Color(0xFFE7EAF9)
val SF_Light_Up            = Color(0xFF1F7A4D)
val SF_Light_Down          = Color(0xFFB43A3A)

// ─── CompositionLocals ────────────────────────────────────────────────────────

/** Prisrörelse uppåt/nedåt — adapterar automatiskt till aktivt tema. */
val LocalPriceUp   = compositionLocalOf { SF_Light_Up }
val LocalPriceDown = compositionLocalOf { SF_Light_Down }

/**
 * Utlöst bevakning — samma accentfärg som resten av appen (en enda accent).
 * [LocalTriggeredBadge] är bakgrunden, [LocalOnTriggeredBadge] är texten/ikonen på den.
 */
val LocalTriggeredBadge   = compositionLocalOf { SF_Light_Accent }
val LocalOnTriggeredBadge = compositionLocalOf { SF_Light_OnAccent }

/** Tunn linje mellan rader/sektioner. */
val LocalCardBorder = compositionLocalOf { SF_Light_Line }

/** Sekundär text (etiketter, metadata). */
val LocalTextTertiary = compositionLocalOf { SF_Light_TextSecondary }
