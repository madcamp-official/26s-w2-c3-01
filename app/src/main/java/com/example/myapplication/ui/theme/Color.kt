package com.example.myapplication.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SyncPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val primary: Color,
    val primarySoft: Color,
    val text: Color,
    val textMuted: Color,
    val error: Color = Color(0xFFFF8F82),
    val onError: Color = Color(0xFF2A0704),
)

val VioletGlowPalette = SyncPalette(
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF151517),
    surfaceRaised = Color(0xFF202024),
    border = Color(0xFF38383F),
    primary = Color(0xFFB8A7FF),
    primarySoft = Color(0xFFD8CEFF),
    text = Color(0xFFF7F7FA),
    textMuted = Color(0xFFA6A6B0),
)

val CurrentSyncPalette: SyncPalette
    get() = VioletGlowPalette

val Ink: Color get() = CurrentSyncPalette.background
val InkRaised: Color get() = CurrentSyncPalette.surface
val MossSurface: Color get() = CurrentSyncPalette.surface
val MossSurfaceHigh: Color get() = CurrentSyncPalette.surfaceRaised
val SignalGreen: Color get() = CurrentSyncPalette.primary
val SignalGreenSoft: Color get() = CurrentSyncPalette.primarySoft
val PaleMint: Color get() = CurrentSyncPalette.text
val MutedMint: Color get() = CurrentSyncPalette.textMuted
val MossOutline: Color get() = CurrentSyncPalette.border
val WarmAmber: Color get() = CurrentSyncPalette.primarySoft
val Coral: Color get() = CurrentSyncPalette.error
