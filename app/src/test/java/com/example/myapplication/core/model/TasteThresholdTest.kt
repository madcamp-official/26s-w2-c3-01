package com.example.myapplication.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteThresholdTest {
    @Test
    fun `threshold uses actual server score`() {
        assertTrue(listener(score = 76).matchesTasteThreshold(75))
        assertFalse(listener(score = 74).matchesTasteThreshold(75))
    }

    @Test
    fun `insufficient evidence remains discoverable without a fabricated score`() {
        assertTrue(listener(score = null).matchesTasteThreshold(90))
    }

    private fun listener(score: Int?) = NearbyListener(
        nearbyHandle = "nearby-test",
        displayAlias = "테스트",
        colorHex = 0L,
        displayPosition = DisplayPosition(0.5f, 0.5f),
        matchScore = 50,
        proximity = Proximity.WITHIN_10M,
        isPlaying = false,
        currentTrack = null,
        commonGenres = emptyList(),
        tasteMatch = CommonTasteSummary(
            score = score,
            confidence = "LOW",
            metrics = emptyList(),
            algorithmVersion = "test",
            sampleSize = 2,
            calculatedAt = "2026-07-15T00:00:00Z",
        ),
    )
}
