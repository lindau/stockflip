package com.stockflip.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Nordisk Precision — Dark Palette ────────────────────────────────────────

val NP_Dark_Background    = Color(0xFF09111D)
val NP_Dark_Surface       = Color(0xFF101A27)
val NP_Dark_SurfaceAlt    = Color(0xFF152233)
val NP_Dark_SurfaceHigh   = Color(0xFF1A2A3D)
val NP_Dark_Primary       = Color(0xFF3AB7A4)
val NP_Dark_PrimaryMuted  = Color(0xFF2B8C80)
val NP_Dark_Secondary     = Color(0xFF7FA8C9)
val NP_Dark_Positive      = Color(0xFF3CCB7F)
val NP_Dark_Negative      = Color(0xFFE06C75)
val NP_Dark_Warning       = Color(0xFFE7B65C)
val NP_Dark_TextPrimary   = Color(0xFFF2F5F7)
val NP_Dark_TextSecondary = Color(0xFFB5C0CB)
val NP_Dark_TextTertiary  = Color(0xFF7F8C99)
val NP_Dark_Outline       = Color(0xFF263648)
val NP_Dark_OutlineSoft   = Color(0xFF1D2A38)
val NP_Dark_Chip          = Color(0xFF132131)
val NP_Dark_ChipSelected  = Color(0xFF1B3C44)
val NP_Dark_CardBorder              = Color(0xFF213345)
val NP_Dark_SurfaceContainerLow    = Color(0xFF122030)  // Stat-celler: mellan Surface och SurfaceAlt
val NP_Dark_TriggeredContainer      = Color(0xFF231A08)
val NP_Dark_ErrorContainer          = Color(0xFF3D1A1D)

// ─── Nordisk Precision — Light Palette ───────────────────────────────────────

val NP_Light_Background    = Color(0xFFF5F8FB)
val NP_Light_Surface       = Color(0xFFFFFFFF)
val NP_Light_SurfaceAlt    = Color(0xFFF0F4F8)
val NP_Light_SurfaceHigh   = Color(0xFFE7EEF5)
val NP_Light_Primary       = Color(0xFF1F8A7A)
val NP_Light_PrimaryMuted  = Color(0xFF2F6E67)
val NP_Light_Secondary     = Color(0xFF5E7FA3)
val NP_Light_Positive      = Color(0xFF238B57)
val NP_Light_Negative      = Color(0xFFB94A5A)
val NP_Light_Warning       = Color(0xFFB8842F)
val NP_Light_TextPrimary   = Color(0xFF10202E)
val NP_Light_TextSecondary = Color(0xFF516273)
val NP_Light_TextTertiary  = Color(0xFF7A8897)
val NP_Light_Outline       = Color(0xFFD5DEE7)
val NP_Light_OutlineSoft   = Color(0xFFE6EDF3)
val NP_Light_Chip          = Color(0xFFEAF1F6)
val NP_Light_ChipSelected  = Color(0xFFD8ECE8)
val NP_Light_CardBorder              = Color(0xFFD6E0E8)
val NP_Light_SurfaceContainerLow    = Color(0xFFF4F7FA)  // Stat-celler: subtilt off-white mot kortets vita yta
val NP_Light_TriggeredContainer      = Color(0xFFFFF3DD)
val NP_Light_ErrorContainer          = Color(0xFFF9E0E3)

// ─── CompositionLocals ────────────────────────────────────────────────────────

/** Prisrörelse uppåt/nedåt — adapterar automatiskt till aktivt tema. */
val LocalPriceUp   = compositionLocalOf { NP_Light_Positive }
val LocalPriceDown = compositionLocalOf { NP_Light_Negative }

/** Trendpil i MetricAlertCard — semantiskt skilt från prisrörelse. */
val LocalTrendUp   = compositionLocalOf { NP_Light_Positive }
val LocalTrendDown = compositionLocalOf { NP_Light_Negative }

/**
 * Triggered-badge — amber/warning-ton, lågmält men distinkt.
 * [LocalTriggeredBadge] är badge-bakgrunden, [LocalOnTriggeredBadge] är texten på den.
 */
val LocalTriggeredBadge   = compositionLocalOf { NP_Light_Warning }
val LocalOnTriggeredBadge = compositionLocalOf { Color.White }

/** Kortborder — subtil kontur som skiljer kort från yta utan skugga. */
val LocalCardBorder = compositionLocalOf { NP_Light_CardBorder }

/** Tertiär text — metadatanivå under onSurfaceVariant; tickers, labels, tidsstämplar. */
val LocalTextTertiary = compositionLocalOf { NP_Light_TextTertiary }
