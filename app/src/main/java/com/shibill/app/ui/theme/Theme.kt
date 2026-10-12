package com.shibill.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA2F1E8), onPrimaryContainer = Color(0xFF00201D),
    inversePrimary = Color(0xFF86D5CC),
    secondary = Color(0xFF4A6360), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF051F1D),
    tertiary = Color(0xFF456179), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9E6FF), onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFFF5FAF8), onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF5FAF8), onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF0F766E),
    surfaceBright = Color(0xFFF5FAF8), surfaceDim = Color(0xFFD5DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED), surfaceContainerHigh = Color(0xFFE4EAE8),
    surfaceContainerHighest = Color(0xFFDEE5E2),
    inverseSurface = Color(0xFF2C3231), inverseOnSurface = Color(0xFFEDF2F1),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D5CC), onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF005049), onPrimaryContainer = Color(0xFFA2F1E8),
    inversePrimary = Color(0xFF0F766E),
    secondary = Color(0xFFB1CCC8), onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF334B48), onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFADCAE4), onTertiary = Color(0xFF0F3450),
    tertiaryContainer = Color(0xFF2C4A60), onTertiaryContainer = Color(0xFFC9E6FF),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceTint = Color(0xFF86D5CC),
    surfaceBright = Color(0xFF343A39), surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0E), surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
    inverseSurface = Color(0xFFDEE4E2), inverseOnSurface = Color(0xFF2C3231),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899391), outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000)
)

@Composable
fun ShiBillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ProvideSemanticColors(darkTheme = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
