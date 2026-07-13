package com.example.myapplication.ui

import com.example.myapplication.core.model.MusicSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingPreviewMatcherTest {
    @Test
    fun returnsHttpsPreviewForExactNormalizedTrackAndArtist() {
        val results = listOf(
            result(
                title = "Blue Night",
                artist = "Wave to Earth",
                previewUrl = "https://audio.example/preview.m4a",
            )
        )

        assertEquals(
            "https://audio.example/preview.m4a",
            results.matchingPreviewUrl(" blue-night ", "WAVE TO EARTH"),
        )
    }

    @Test
    fun rejectsDifferentArtistAndNonHttpsPreview() {
        val wrongArtist = listOf(
            result("Blue Night", "Another Artist", "https://audio.example/wrong.m4a")
        )
        val insecureUrl = listOf(
            result("Blue Night", "Wave to Earth", "http://audio.example/preview.m4a")
        )

        assertNull(wrongArtist.matchingPreviewUrl("Blue Night", "Wave to Earth"))
        assertNull(insecureUrl.matchingPreviewUrl("Blue Night", "Wave to Earth"))
    }

    private fun result(title: String, artist: String, previewUrl: String?) = MusicSearchResult(
        id = 1L,
        artistId = 2L,
        title = title,
        artist = artist,
        album = "Album",
        genre = "Indie",
        releaseDate = null,
        durationSeconds = 180,
        artworkUrl = null,
        previewUrl = previewUrl,
        appleMusicUrl = null,
    )
}
