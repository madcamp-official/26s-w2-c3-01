package com.example.myapplication.ui

import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.DisplayPosition
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.Proximity
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

    @Test
    fun acceptsUniqueExactTitleWhenStorefrontLocalizesArtistName() {
        val localizedPreview = "https://audio.example/busy-boy.m4a"
        val results = listOf(
            result("Busy Boy", "리센느", localizedPreview),
            result("Busy Boy (Galantis Remix)", "리센느", "https://audio.example/remix.m4a"),
        )

        assertEquals(localizedPreview, results.matchingPreviewUrl("Busy Boy", "RESCENE"))
    }

    @Test
    fun rejectsLocalizedArtistFallbackWhenExactTitleIsAmbiguous() {
        val results = listOf(
            result("Stay", "스테이 원", "https://audio.example/one.m4a"),
            result("Stay", "스테이 투", "https://audio.example/two.m4a"),
        )

        assertNull(results.matchingPreviewUrl("Stay", "STAY"))
    }

    @Test
    fun removesMelonLocalizedArtistQualifierFromSearchAndMatching() {
        val officialPreview = "https://audio.example/meow.m4a"
        val results = listOf(
            result("MEOW", "MEOVV", officialPreview),
            result(
                "TOXIC (By MEOVV(미야오)) [Melody Karaoke Version]",
                "ZZang KARAOKE",
                "https://audio.example/karaoke.m4a",
            ),
        )

        assertEquals("MEOW MEOVV", musicPreviewSearchTerm("MEOW", "MEOVV (미야오)"))
        assertEquals(officialPreview, results.matchingPreviewUrl("MEOW", "MEOVV (미야오)"))
    }

    @Test
    fun supportsFullWidthParenthesesFromLocalizedMetadata() {
        assertEquals("ILLIT", "ILLIT（아일릿）".withoutParentheticalQualifier())
    }

    @Test
    fun previewSourceSurvivesNearbySessionHandleRotation() {
        val oldSessionHandle = "session-old"
        val currentSession = nearbyListener(
            nearbyHandle = "session-current",
            profileHandle = "same-user",
        )

        val resolved = listOf(currentSession).findPreviewSource(
            nearbyHandle = oldSessionHandle,
            profileHandle = "SAME-USER",
        )

        assertEquals(currentSession, resolved)
    }

    @Test
    fun previewSourceFallsBackToSessionHandleWithoutProfileIdentity() {
        val currentSession = nearbyListener(nearbyHandle = "session-current", profileHandle = null)

        assertEquals(
            currentSession,
            listOf(currentSession).findPreviewSource("session-current", null),
        )
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

    private fun nearbyListener(nearbyHandle: String, profileHandle: String?) = NearbyListener(
        nearbyHandle = nearbyHandle,
        profileHandle = profileHandle,
        displayAlias = "Listener",
        colorHex = 0xFF6750A4L,
        displayPosition = DisplayPosition(0.5f, 0.5f),
        matchScore = 80,
        proximity = Proximity.WITHIN_10M,
        isPlaying = true,
        currentTrack = null,
        commonGenres = emptyList(),
    )
}
