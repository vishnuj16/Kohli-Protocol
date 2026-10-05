package com.vishnu.kohliprotocol.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val scheme = darkColorScheme(
    primary = KohliColors.Accent,
    onPrimary = KohliColors.OnAccent,
    primaryContainer = KohliColors.Accent.copy(alpha = 0.16f),
    onPrimaryContainer = KohliColors.Accent,
    secondary = KohliColors.Logged,
    onSecondary = KohliColors.OnAccent,
    secondaryContainer = KohliColors.Accent.copy(alpha = 0.16f),
    onSecondaryContainer = KohliColors.Accent,
    background = KohliColors.Background,
    onBackground = KohliColors.Text,
    surface = KohliColors.Surface,
    onSurface = KohliColors.Text,
    surfaceVariant = KohliColors.SurfaceHigh,
    onSurfaceVariant = KohliColors.Muted,
    surfaceContainerLowest = KohliColors.Background,
    surfaceContainerLow = KohliColors.Surface,
    surfaceContainer = KohliColors.Surface,
    surfaceContainerHigh = KohliColors.SurfaceHigh,
    surfaceContainerHighest = KohliColors.SurfaceHigh,
    outline = KohliColors.Outline,
    outlineVariant = KohliColors.Outline,
    error = KohliColors.Missing,
    onError = KohliColors.Text,
)

val KohliShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Kohli Protocol is dark-only. */
@Composable
fun KohliTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = KohliTypography, shapes = KohliShapes, content = content)
}
