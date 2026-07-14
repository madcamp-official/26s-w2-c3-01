package com.example.myapplication

import com.example.myapplication.core.model.PopularTrack
import com.example.myapplication.core.model.Track
import com.example.myapplication.data.preservingResolvedArtwork
import org.junit.Assert.assertEquals
import org.junit.Test

class PopularTrackArtworkTest {
    @Test
    fun serverNullDoesNotEraseResolvedArtwork() {
        val previous = popularTrack("https://image.example/cover.jpg")
        val incoming = popularTrack(null)

        val merged = listOf(incoming).preservingResolvedArtwork(listOf(previous)).single()

        assertEquals("https://image.example/cover.jpg", merged.track.artworkUrl)
    }

    @Test
    fun newerServerArtworkReplacesResolvedArtwork() {
        val previous = popularTrack("https://image.example/old.jpg")
        val incoming = popularTrack("https://image.example/new.jpg")

        val merged = listOf(incoming).preservingResolvedArtwork(listOf(previous)).single()

        assertEquals("https://image.example/new.jpg", merged.track.artworkUrl)
    }

    private fun popularTrack(artworkUrl: String?) = PopularTrack(
        track = Track(
            id = "popular-track",
            title = "It's Me",
            artist = "ILLIT",
            artworkUrl = artworkUrl,
            platform = "SERVER_AGGREGATE",
        ),
        listenerCount = 2,
        reactionCount = 1,
    )
}
