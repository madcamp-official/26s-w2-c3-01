package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MelodyColorScheme = darkColorScheme(
    primary = SignalGreen,
    onPrimary = Color(0xFF00210B),
    primaryContainer = Color(0xFF123E24),
    onPrimaryContainer = PaleMint,
    secondary = SignalGreenSoft,
    onSecondary = Color(0xFF082313),
    background = Ink,
    onBackground = PaleMint,
    surface = InkRaised,
    onSurface = PaleMint,
    surfaceVariant = MossSurface,
    onSurfaceVariant = MutedMint,
    outline = MossOutline,
    error = Coral,
    onError = Color(0xFF2A0704)
)

private val MelodyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MelodyBubbleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MelodyColorScheme,
        typography = Typography,
        shapes = MelodyShapes,
        content = content
    )
}
