package com.example.myapplication.ui

import com.example.myapplication.data.remote.LoungeRecommendationCardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoungeRecommendationPlaybackTest {
    @Test
    fun selectsMostRecentlyCreatedRecommendation() {
        val older = card("older", "2026-07-14T01:00:00Z")
        val newest = card("newest", "2026-07-14T02:00:00Z")

        assertEquals(newest, listOf(newest, older).latestRecommendation())
    }

    @Test
    fun returnsNullWhenThereAreNoRecommendations() {
        assertNull(emptyList<LoungeRecommendationCardDto>().latestRecommendation())
    }

    private fun card(id: String, createdAt: String) = LoungeRecommendationCardDto(
        id = id,
        subLoungeId = "lounge-1",
        clientCardId = "client-$id",
        senderAlias = "listener",
        trackTitle = "Track $id",
        artistName = "Artist",
        message = null,
        reactionCount = 0,
        reactedByMe = false,
        createdAt = createdAt,
    )
}
