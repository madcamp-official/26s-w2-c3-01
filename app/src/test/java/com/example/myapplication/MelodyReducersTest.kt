package com.example.myapplication

import com.example.myapplication.core.model.DisplayPosition
import com.example.myapplication.core.model.LoungePoll
import com.example.myapplication.core.model.MelodyReducers
import com.example.myapplication.core.model.NearbyDelta
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.PollOption
import com.example.myapplication.core.model.Proximity
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.data.remote.ApiEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyReducersTest {
    @Test
    fun staleNearbyDeltaIsIgnored() {
        val listener = listener("mint", 80)
        val result = MelodyReducers.applyNearbyDelta(
            currentSequence = 10,
            current = listOf(listener),
            delta = NearbyDelta(sequence = 9, leftHandles = setOf("mint"))
        )

        assertFalse(result.applied)
        assertEquals(listOf(listener), result.listeners)
        assertEquals(10, result.sequence)
    }

    @Test
    fun nearbyDeltaAppliesEnterUpdateAndLeaveByOpaqueHandle() {
        val result = MelodyReducers.applyNearbyDelta(
            currentSequence = 10,
            current = listOf(listener("mint", 80), listener("wave", 70)),
            delta = NearbyDelta(
                sequence = 11,
                entered = listOf(listener("nova", 92)),
                updated = listOf(listener("mint", 95)),
                leftHandles = setOf("wave")
            )
        )

        assertTrue(result.applied)
        assertEquals(11, result.sequence)
        assertEquals(listOf("mint", "nova"), result.listeners.map { it.nearbyHandle })
    }

    @Test
    fun switchingVoteMovesOneVoteAndKeepsSingleChoice() {
        val poll = LoungePoll(
            id = "poll",
            question = "분위기",
            options = listOf(PollOption("indie", "Indie", 4), PollOption("rnb", "R&B", 3)),
            myChoice = "indie"
        )

        val result = MelodyReducers.applyVote(poll, "rnb")

        assertEquals("rnb", result.myChoice)
        assertEquals(3, result.options.first { it.id == "indie" }.voteCount)
        assertEquals(4, result.options.first { it.id == "rnb" }.voteCount)
        assertEquals(7, result.totalVotes)
    }

    @Test
    fun chatRequiresMutualFollowAndNonBlankMessage() {
        assertEquals(
            "맞팔 사용자와만 대화할 수 있어요",
            MelodyReducers.chatValidationError("안녕", RelationshipStatus.FOLLOWING)
        )
        assertEquals(
            "메시지를 입력해 주세요",
            MelodyReducers.chatValidationError("   ", RelationshipStatus.MUTUAL)
        )
        assertNull(MelodyReducers.chatValidationError("안녕", RelationshipStatus.MUTUAL))
    }

    @Test
    fun clientNearbyModelHasNoCoordinateOrExactDistanceFields() {
        val forbiddenTokens = setOf("latitude", "longitude", "distance", "bearing", "accuracy")
        val fieldNames = NearbyListener::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fieldNames.none { field -> forbiddenTokens.any(field::contains) })
    }

    @Test
    fun blankEnvironmentKeepsDemoModeAvailable() {
        assertFalse(ApiEnvironment(apiBaseUrl = "", stompWsUrl = "").isConfigured)
        assertTrue(
            ApiEnvironment(
                apiBaseUrl = "https://api.example.test",
                stompWsUrl = "wss://api.example.test/ws"
            ).isConfigured
        )
    }

    private fun listener(handle: String, score: Int) = NearbyListener(
        nearbyHandle = handle,
        displayAlias = handle.replaceFirstChar { it.uppercase() },
        colorHex = 0xFF25C76FL,
        displayPosition = DisplayPosition(0.3f, 0.4f),
        matchScore = score,
        proximity = Proximity.WITHIN_10M,
        isPlaying = false,
        currentTrack = null,
        commonGenres = emptyList()
    )
}
