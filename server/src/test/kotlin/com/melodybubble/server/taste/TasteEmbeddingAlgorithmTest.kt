package com.melodybubble.server.taste

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteEmbeddingAlgorithmTest {
    @Test
    fun `matching is symmetric and deterministic`() {
        val first = TasteEmbeddingAlgorithm.encode(
            listOf(
                TasteSignal("Indie", "GENRE", 2.0),
                TasteSignal("DAY6", "ARTIST", 1.5),
                TasteSignal("예뻤어 · DAY6", "TRACK", 1.0),
            )
        )
        val second = TasteEmbeddingAlgorithm.encode(
            listOf(
                TasteSignal("Indie", "GENRE", 2.0),
                TasteSignal("DAY6", "ARTIST", 1.5),
                TasteSignal("예뻤어 · DAY6", "TRACK", 1.0),
            )
        )

        val forward = TasteEmbeddingAlgorithm.compare(first, second)
        val reverse = TasteEmbeddingAlgorithm.compare(second, first)

        assertEquals(forward.score, reverse.score)
        assertEquals(forward.confidence, reverse.confidence)
        assertEquals(forward.metrics, reverse.metrics)
        assertTrue(requireNotNull(forward.score) > 65)
    }

    @Test
    fun `insufficient evidence never exposes a fabricated percentage`() {
        val first = TasteEmbeddingAlgorithm.encode(listOf(TasteSignal("Rock", "GENRE", 2.0)))
        val second = TasteEmbeddingAlgorithm.encode(listOf(TasteSignal("Rock", "GENRE", 2.0)))

        val result = TasteEmbeddingAlgorithm.compare(first, second)

        assertNull(result.score)
        assertEquals("LOW", result.confidence)
    }

    @Test
    fun `confidence shrinkage prevents sparse profiles from showing perfect match`() {
        val signals = listOf(
            TasteSignal("R&B", "GENRE", 2.0),
            TasteSignal("SZA", "ARTIST", 1.5),
            TasteSignal("Good Days · SZA", "TRACK", 1.0),
        )

        val result = TasteEmbeddingAlgorithm.compare(
            TasteEmbeddingAlgorithm.encode(signals),
            TasteEmbeddingAlgorithm.encode(signals),
        )

        assertEquals("LOW", result.confidence)
        assertTrue(requireNotNull(result.score) in 65..75)
    }

    @Test
    fun `normalization maps high confidence cosine bounds to zero and one hundred`() {
        assertEquals(0, TasteEmbeddingAlgorithm.normalizedSimilarityScore(-1.0, "HIGH"))
        assertEquals(50, TasteEmbeddingAlgorithm.normalizedSimilarityScore(0.0, "HIGH"))
        assertEquals(100, TasteEmbeddingAlgorithm.normalizedSimilarityScore(1.0, "HIGH"))
    }

    @Test
    fun `unsupported taste signal types are excluded from the vector`() {
        val vector = TasteEmbeddingAlgorithm.encode(
            listOf(TasteSignal("legacy", "REMOVED_TYPE", 5.0))
        )

        assertEquals(0, vector.evidenceCount)
    }
}
