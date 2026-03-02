package com.stockflip.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Pine & Sand Theme - Light
val PinePrimary = Color(0xFF2F5D50)
val PineOnPrimary = Color(0xFFFFFFFF)
val PinePrimaryContainer = Color(0xFFDDEBE5)
val PineOnPrimaryContainer = Color(0xFF0E1F1A)

val SandSecondary = Color(0xFF6B5E4B)
val SandOnSecondary = Color(0xFFFFFFFF)
val SandSecondaryContainer = Color(0xFFF0E9DE)
val SandOnSecondaryContainer = Color(0xFF1A1612)

val PineBackground = Color(0xFFF6F2EA)
val PineOnBackground = Color(0xFF1A1612)

val PineSurface = Color(0xFFFFFCF7)
val PineOnSurface = Color(0xFF1A1612)
val PineSurfaceVariant = Color(0xFFEFE7DB)
val PineOnSurfaceVariant = Color(0xFF4A4034)

val PineOutline = Color(0xFFCFC3B3)
val PineError = Color(0xFFB3261E)
val PineOnError = Color(0xFFFFFFFF)
val PineErrorContainer = Color(0xFFF9DEDC)
val PineOnErrorContainer = Color(0xFF410E0B)

// Dark Theme - Navy Blue + Bright Green
val PinePrimaryDark = Color(0xFF2ECC71)
val PineOnPrimaryDark = Color(0xFF003912)
val PinePrimaryContainerDark = Color(0xFF0A2B18)
val PineOnPrimaryContainerDark = Color(0xFFA0EAB5)

val SandSecondaryDark = Color(0xFF8B9BB4)
val SandOnSecondaryDark = Color(0xFF0D1426)
val SandSecondaryContainerDark = Color(0xFF1E2E44)
val SandOnSecondaryContainerDark = Color(0xFFC8D8EC)

val PineBackgroundDark = Color(0xFF0D1426)
val PineOnBackgroundDark = Color(0xFFFFFFFF)

val PineSurfaceDark = Color(0xFF192438)
val PineOnSurfaceDark = Color(0xFFFFFFFF)
val PineSurfaceVariantDark = Color(0xFF1C2840)
val PineOnSurfaceVariantDark = Color(0xFF8B9BB4)

val PineOutlineDark = Color(0xFF253347)
val PineErrorDark = Color(0xFFFF4A4A)
val PineOnErrorDark = Color(0xFFFFFFFF)
val PineErrorContainerDark = Color(0xFF5C1A1A)
val PineOnErrorContainerDark = Color(0xFFFFB4AB)

// Status colors
val PriceUp = Color(0xFF2F5D50)     // Pine green for up (light theme)
val PriceDown = Color(0xFFB3261E)   // Error red for down (light theme)
val PriceUpDark = Color(0xFF2ECC71) // Bright green for up (dark theme)
val PriceDownDark = Color(0xFFFF4A4A) // Bright red for down (dark theme)

// CompositionLocals for theme-aware price colors
val LocalPriceUp = compositionLocalOf { PriceUp }
val LocalPriceDown = compositionLocalOf { PriceDown }
