package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private fun syncColorScheme(palette: SyncPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.background,
    primaryContainer = palette.surfaceRaised,
    onPrimaryContainer = palette.text,
    secondary = palette.primarySoft,
    onSecondary = palette.background,
    secondaryContainer = palette.surfaceRaised,
    onSecondaryContainer = palette.text,
    background = palette.background,
    onBackground = palette.text,
    surface = palette.surface,
    onSurface = palette.text,
    surfaceVariant = palette.surface,
    onSurfaceVariant = palette.textMuted,
    outline = palette.border,
    outlineVariant = palette.border,
    error = palette.error,
    onError = palette.onError,
)

private val MelodyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun MelodyBubbleTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = syncColorScheme(VioletGlowPalette),
        typography = Typography,
        shapes = MelodyShapes,
        content = content,
    )
}
