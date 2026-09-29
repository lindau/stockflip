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
    primary                = SF_Dark_Accent,
    onPrimary              = SF_Dark_OnAccent,
    primaryContainer       = SF_Dark_AccentSoft,
    onPrimaryContainer     = SF_Dark_Accent,
    secondary              = SF_Dark_TextSecondary,
    onSecondary            = SF_Dark_Background,
    secondaryContainer     = SF_Dark_Surface,
    onSecondaryContainer   = SF_Dark_TextPrimary,
    tertiary               = SF_Dark_Accent,
    onTertiary             = SF_Dark_OnAccent,
    tertiaryContainer      = SF_Dark_AccentSoft,
    onTertiaryContainer    = SF_Dark_Accent,
    background             = SF_Dark_Background,
    onBackground           = SF_Dark_TextPrimary,
    surface                = SF_Dark_Background,
    onSurface              = SF_Dark_TextPrimary,
    surfaceVariant         = SF_Dark_Surface,
    onSurfaceVariant       = SF_Dark_TextSecondary,
    surfaceContainerLowest = SF_Dark_Background,
    surfaceContainerLow    = SF_Dark_Surface,
    surfaceContainer       = SF_Dark_Surface,
    surfaceContainerHigh   = SF_Dark_SurfaceHigh,
    surfaceContainerHighest = SF_Dark_SurfaceHigh,
    outline                = SF_Dark_TextSecondary,
    outlineVariant         = SF_Dark_Line,
    error                  = SF_Dark_Down,
    onError                = SF_Dark_Background,
    errorContainer         = SF_Dark_Surface,
    onErrorContainer       = SF_Dark_Down,
)

private val LightColorScheme = lightColorScheme(
    primary                = SF_Light_Accent,
    onPrimary              = SF_Light_OnAccent,
    primaryContainer       = SF_Light_AccentSoft,
    onPrimaryContainer     = SF_Light_Accent,
    secondary              = SF_Light_TextSecondary,
    onSecondary            = Color.White,
    secondaryContainer     = SF_Light_Surface,
    onSecondaryContainer   = SF_Light_TextPrimary,
    tertiary               = SF_Light_Accent,
    onTertiary             = SF_Light_OnAccent,
    tertiaryContainer      = SF_Light_AccentSoft,
    onTertiaryContainer    = SF_Light_Accent,
    background             = SF_Light_Background,
    onBackground           = SF_Light_TextPrimary,
    surface                = SF_Light_Background,
    onSurface              = SF_Light_TextPrimary,
    surfaceVariant         = SF_Light_Surface,
    onSurfaceVariant       = SF_Light_TextSecondary,
    surfaceContainerLowest = SF_Light_Background,
    surfaceContainerLow    = SF_Light_Surface,
    surfaceContainer       = SF_Light_Surface,
    surfaceContainerHigh   = SF_Light_SurfaceHigh,
    surfaceContainerHighest = SF_Light_SurfaceHigh,
    outline                = SF_Light_TextSecondary,
    outlineVariant         = SF_Light_Line,
    error                  = SF_Light_Down,
    onError                = Color.White,
    errorContainer         = SF_Light_Surface,
    onErrorContainer       = SF_Light_Down,
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
            // Status bar matchar bakgrunden
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalPriceUp          provides if (darkTheme) SF_Dark_Up            else SF_Light_Up,
        LocalPriceDown        provides if (darkTheme) SF_Dark_Down          else SF_Light_Down,
        LocalTriggeredBadge   provides if (darkTheme) SF_Dark_Accent        else SF_Light_Accent,
        LocalOnTriggeredBadge provides if (darkTheme) SF_Dark_OnAccent      else SF_Light_OnAccent,
        LocalCardBorder       provides if (darkTheme) SF_Dark_Line          else SF_Light_Line,
        LocalTextTertiary     provides if (darkTheme) SF_Dark_TextSecondary else SF_Light_TextSecondary,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = Shapes,
            content     = content
        )
    }
}
