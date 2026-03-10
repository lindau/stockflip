package com.stockflip.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary                = NP_Dark_Primary,
    onPrimary              = NP_Dark_Background,
    primaryContainer       = NP_Dark_ChipSelected,
    onPrimaryContainer     = NP_Dark_Primary,
    secondary              = NP_Dark_Secondary,
    onSecondary            = NP_Dark_Background,
    secondaryContainer     = NP_Dark_Chip,
    onSecondaryContainer   = NP_Dark_TextSecondary,
    tertiary               = NP_Dark_Warning,
    onTertiary             = NP_Dark_Background,
    tertiaryContainer      = NP_Dark_TriggeredContainer,
    onTertiaryContainer    = NP_Dark_Warning,
    background             = NP_Dark_Background,
    onBackground           = NP_Dark_TextPrimary,
    surface                = NP_Dark_Surface,
    onSurface              = NP_Dark_TextPrimary,
    surfaceVariant         = NP_Dark_SurfaceAlt,
    onSurfaceVariant       = NP_Dark_TextSecondary,
    surfaceContainerLow    = NP_Dark_SurfaceContainerLow,
    surfaceContainer       = NP_Dark_SurfaceAlt,
    surfaceContainerHigh   = NP_Dark_SurfaceHigh,
    outline                = NP_Dark_Outline,
    outlineVariant         = NP_Dark_OutlineSoft,
    error                  = NP_Dark_Negative,
    onError                = NP_Dark_Background,
    errorContainer         = NP_Dark_ErrorContainer,
    onErrorContainer       = NP_Dark_Negative,
)

private val LightColorScheme = lightColorScheme(
    primary                = NP_Light_Primary,
    onPrimary              = Color.White,
    primaryContainer       = NP_Light_ChipSelected,
    onPrimaryContainer     = NP_Light_TextPrimary,
    secondary              = NP_Light_Secondary,
    onSecondary            = Color.White,
    secondaryContainer     = NP_Light_Chip,
    onSecondaryContainer   = NP_Light_TextPrimary,
    tertiary               = NP_Light_Warning,
    onTertiary             = Color.White,
    tertiaryContainer      = NP_Light_TriggeredContainer,
    onTertiaryContainer    = NP_Light_TextPrimary,
    background             = NP_Light_Background,
    onBackground           = NP_Light_TextPrimary,
    surface                = NP_Light_Surface,
    onSurface              = NP_Light_TextPrimary,
    surfaceVariant         = NP_Light_SurfaceAlt,
    onSurfaceVariant       = NP_Light_TextSecondary,
    surfaceContainerLow    = NP_Light_SurfaceContainerLow,
    surfaceContainer       = NP_Light_SurfaceAlt,
    surfaceContainerHigh   = NP_Light_SurfaceHigh,
    outline                = NP_Light_Outline,
    outlineVariant         = NP_Light_OutlineSoft,
    error                  = NP_Light_Negative,
    onError                = Color.White,
    errorContainer         = NP_Light_ErrorContainer,
    onErrorContainer       = NP_Light_TextPrimary,
)

@Composable
fun StockFlipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar matchar bakgrunden, inte ytan — ger djup överst
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalPriceUp          provides if (darkTheme) NP_Dark_Positive      else NP_Light_Positive,
        LocalPriceDown        provides if (darkTheme) NP_Dark_Negative      else NP_Light_Negative,
        LocalTrendUp          provides if (darkTheme) NP_Dark_Positive      else NP_Light_Positive,
        LocalTrendDown        provides if (darkTheme) NP_Dark_Negative      else NP_Light_Negative,
        LocalTriggeredBadge   provides if (darkTheme) NP_Dark_Warning       else NP_Light_Warning,
        // Dark: mörk text på amber (#09111D/#E7B65C → 11.7:1 ✓)
        // Light: mörk text på amber (#10202E/#B8842F → 6.8:1 ✓) — vit misslyckas (~3.1:1)
        LocalOnTriggeredBadge provides if (darkTheme) NP_Dark_Background    else NP_Light_TextPrimary,
        LocalCardBorder       provides if (darkTheme) NP_Dark_CardBorder    else NP_Light_CardBorder,
        LocalTextTertiary     provides if (darkTheme) NP_Dark_TextTertiary  else NP_Light_TextTertiary,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = Shapes,
            content     = content
        )
    }
}
