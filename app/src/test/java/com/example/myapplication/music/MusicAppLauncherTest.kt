package com.example.myapplication.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAppLauncherTest {
    @Test
    fun `last active playback app wins over curated and resolver order`() {
        val result = orderedMusicPackages(
            lastPlaybackPackage = "com.iloen.melon",
            defaultPackage = "com.spotify.music",
            candidates = listOf("com.spotify.music", "com.iloen.melon", "example.player"),
        )

        assertEquals(listOf("com.iloen.melon", "com.spotify.music", "example.player"), result)
    }

    @Test
    fun `browser and regular youtube are never considered music targets`() {
        val result = orderedMusicPackages(
            lastPlaybackPackage = "com.android.chrome",
            defaultPackage = "com.google.android.youtube",
            candidates = listOf(
                "com.android.chrome",
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
            ),
        )

        assertEquals(listOf("com.google.android.apps.youtube.music"), result)
    }

    @Test
    fun `device default wins when there is no active playback preference`() {
        val result = orderedMusicPackages(
            lastPlaybackPackage = null,
            defaultPackage = "example.device.music",
            candidates = listOf("com.spotify.music", "example.device.music"),
        )

        assertEquals(listOf("example.device.music", "com.spotify.music"), result)
    }

    @Test
    fun `automatic playback presence accepts music apps only`() {
        assertTrue(isRecognizedMusicPlaybackPackage("com.spotify.music"))
        assertTrue(isRecognizedMusicPlaybackPackage("com.google.android.apps.youtube.music"))
        assertTrue(isRecognizedMusicPlaybackPackage("com.iloen.melon"))

        assertFalse(isRecognizedMusicPlaybackPackage("com.google.android.youtube"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.google.android.videos"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.android.chrome"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.netflix.mediaclient"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.samsung.android.video"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.iloen.melonticket"))
        assertFalse(isRecognizedMusicPlaybackPackage("com.sec.android.gallery3d"))
    }
}
