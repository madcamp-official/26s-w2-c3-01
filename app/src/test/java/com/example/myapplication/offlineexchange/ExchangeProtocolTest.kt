package com.example.myapplication.offlineexchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeProtocolTest {
    private val firstCard = ExchangeMusicCard(
        displayAlias = "Mint",
        trackTitle = "First Song",
        trackArtist = "Artist A",
        melodyAlias = "C6 · E6",
        genreTags = listOf("Pop", "Indie"),
        moodTags = listOf("Bright"),
    )
    private val secondCard = ExchangeMusicCard(
        displayAlias = "Blue",
        trackTitle = "Second Song",
        trackArtist = "Artist B",
        melodyAlias = "A5 · B5",
        genreTags = listOf("Jazz"),
        moodTags = listOf("Calm"),
    )

    @Test
    fun `both participants derive the same canonical payload`() {
        val firstOrder = ExchangeProtocol.payloadCanonical("credential-a", firstCard, "credential-b", secondCard)
        val reverseOrder = ExchangeProtocol.payloadCanonical("credential-b", secondCard, "credential-a", firstCard)

        assertEquals(firstOrder, reverseOrder)
    }

    @Test
    fun `music card survives local json storage`() {
        val restored = ExchangeProtocol.cardFromJson(ExchangeProtocol.cardJson(firstCard))

        assertEquals(firstCard, restored)
    }

    @Test
    fun `record signature canonical includes both credentials and protocol`() {
        val canonical = ExchangeProtocol.recordCanonical(
            "exchange-id", "credential-a", "credential-b", "payload-hash", 1, 123L,
        )

        assertTrue(canonical.endsWith("3:123"))
        assertTrue(canonical.contains("credential-b"))
    }
}
