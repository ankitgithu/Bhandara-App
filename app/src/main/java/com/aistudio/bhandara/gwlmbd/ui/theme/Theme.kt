package com.aistudio.bhandara.gwlmbd.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DeepRedDark,
    onPrimary = DarkBackground,
    primaryContainer = DeepRed,
    onPrimaryContainer = TextPrimaryDark,
    secondary = SaffronDark,
    onSecondary = DarkBackground,
    tertiary = WarmGoldDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkCardSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = DeepRed,
    onPrimary = CardBackgroundLight,
    primaryContainer = DeepRedLight,
    onPrimaryContainer = CardBackgroundLight,
    secondary = SaffronOrange,
    onSecondary = CardBackgroundLight,
    tertiary = WarmGold,
    background = CreamBackground,
    onBackground = TextPrimaryLight,
    surface = CardBackgroundLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CreamBackground,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight
)

@Composable
fun BhandaraTheme(
    themeSetting: String = "system", // "light", "dark", "system"
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeSetting) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BhandaraTypography,
        content = content
    )
}
