package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = CyanCore,
    onPrimary = JarvisVoidBlack,
    primaryContainer = CyanDim,
    onPrimaryContainer = CyanBright,
    secondary = CyanMid,
    onSecondary = JarvisVoidBlack,
    secondaryContainer = JarvisPanelElevated,
    onSecondaryContainer = IceWhite,
    tertiary = AmberPulse,
    onTertiary = JarvisVoidBlack,
    background = JarvisVoidBlack,
    onBackground = IceWhite,
    surface = JarvisDeepSpace,
    onSurface = IceWhite,
    surfaceVariant = JarvisPanelSurface,
    onSurfaceVariant = CyanMutedText,
    outline = JarvisGlassBorder,
    outlineVariant = CyanDim,
    error = CrimsonAlert,
    onError = IceWhite,
    errorContainer = CrimsonSurface,
    onErrorContainer = CrimsonAlert
)

private val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
