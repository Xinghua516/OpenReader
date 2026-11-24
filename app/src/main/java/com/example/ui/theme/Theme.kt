package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TonalPrimary,
    onPrimary = TonalOnPrimary,
    primaryContainer = TonalPrimaryContainer,
    onPrimaryContainer = TonalOnPrimaryContainer,
    inversePrimary = TonalInversePrimary,
    secondary = TonalSecondary,
    onSecondary = TonalOnSecondary,
    secondaryContainer = TonalSecondaryContainer,
    onSecondaryContainer = TonalOnSecondaryContainer,
    tertiary = TonalTertiary,
    onTertiary = TonalOnTertiary,
    tertiaryContainer = TonalTertiaryContainer,
    onTertiaryContainer = TonalOnTertiaryContainer,
    background = TonalSurface,
    onBackground = TonalOnSurface,
    surface = TonalSurface,
    onSurface = TonalOnSurface,
    surfaceVariant = TonalSurfaceContainerHighest,
    onSurfaceVariant = TonalOnSurfaceVariant,
    surfaceTint = TonalPrimaryContainer,
    inverseSurface = TonalInverseSurface,
    inverseOnSurface = TonalInverseOnSurface,
    outline = TonalOutline,
    outlineVariant = TonalOutlineVariant,
    error = TonalError,
    onError = TonalOnError,
    errorContainer = TonalErrorContainer,
    onErrorContainer = TonalOnErrorContainer,
    surfaceDim = TonalSurfaceDim,
    surfaceBright = TonalSurfaceBright,
    surfaceContainerLowest = TonalSurfaceContainerLowest,
    surfaceContainerLow = TonalSurfaceContainerLow,
    surfaceContainer = TonalSurfaceContainer,
    surfaceContainerHigh = TonalSurfaceContainerHigh,
    surfaceContainerHighest = TonalSurfaceContainerHighest
)

private val DarkColorScheme = darkColorScheme(
    primary = TonalDarkPrimary,
    onPrimary = TonalDarkOnPrimary,
    primaryContainer = TonalDarkPrimaryContainer,
    onPrimaryContainer = TonalDarkOnPrimaryContainer,
    secondary = TonalDarkSecondary,
    onSecondary = TonalDarkOnSecondary,
    secondaryContainer = TonalDarkSecondaryContainer,
    onSecondaryContainer = TonalDarkOnSecondaryContainer,
    tertiary = TonalDarkTertiary,
    onTertiary = TonalDarkOnTertiary,
    tertiaryContainer = TonalDarkTertiaryContainer,
    onTertiaryContainer = TonalDarkOnTertiaryContainer,
    background = TonalDarkSurface,
    onBackground = TonalDarkOnSurface,
    surface = TonalDarkSurface,
    onSurface = TonalDarkOnSurface,
    surfaceVariant = TonalDarkSurfaceContainerHighest,
    onSurfaceVariant = TonalDarkOnSurfaceVariant,
    outline = TonalDarkOutline,
    outlineVariant = TonalDarkOutlineVariant,
    surfaceContainerLowest = TonalDarkSurfaceContainerLowest,
    surfaceContainerLow = TonalDarkSurfaceContainerLow,
    surfaceContainer = TonalDarkSurfaceContainer,
    surfaceContainerHigh = TonalDarkSurfaceContainerHigh,
    surfaceContainerHighest = TonalDarkSurfaceContainerHighest
)

val TonalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TonalFolioTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TonalTypography,
        shapes = TonalShapes,
        content = content
    )
}
