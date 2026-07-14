package com.example.myapplication.ui

import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.realtime.RealtimeEventEnvelope
import com.example.myapplication.data.remote.LoungePollOptionDto
import com.example.myapplication.data.remote.LoungePollStateDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubLoungeEventReducerTest {
    @Test
    fun `older snapshot without members is treated as an empty profile list`() {
        val json = """{"id":"room","buildingLoungeId":"building","title":"Room","memberCount":1,"joined":true,"canDelete":false,"listeningStatuses":[],"cards":[],"poll":{"options":[],"myVote":null},"generatedAt":"2026-07-12T01:00:00Z"}"""

        val decoded = Gson().fromJson(json, SubLoungeSnapshotDto::class.java)

        assertTrue(decoded.members.orEmpty().isEmpty())
    }

    @Test
    fun `member event updates count without snapshot request`() {
        val reduced = SubLoungeEventReducer.reduce(
            snapshot(),
            event("SUB_LOUNGE_MEMBER_JOINED", """{"memberCount":3,"memberAlias":"새 사용자"}"""),
        )

        assertEquals(3, reduced?.memberCount)
        assertEquals("2026-07-12T02:00:00Z", reduced?.generatedAt)
    }

    @Test
    fun `stopped listening event removes listener`() {
        val playing = event(
            "LISTENING_STATUS_UPDATED",
            """{"listenerAlias":"민아","trackTitle":"곡","artistName":"가수","isPlaying":true,"updatedAt":"2026-07-12T02:00:00Z"}""",
        )
        val started = SubLoungeEventReducer.reduce(snapshot(), playing)!!
        val stopped = SubLoungeEventReducer.reduce(
            started,
            event(
                "LISTENING_STATUS_UPDATED",
                """{"listenerAlias":"민아","isPlaying":false,"updatedAt":"2026-07-12T02:01:00Z"}""",
            ),
        )

        assertTrue(stopped!!.listeningStatuses.isEmpty())
    }

    @Test
    fun `card reaction preserves personal reacted state`() {
        val cardJson = """{"id":"card","subLoungeId":"room","clientCardId":"client","senderAlias":"민아","trackTitle":"곡","artistName":"가수","reactionCount":0,"reactedByMe":false,"createdAt":"2026-07-12T02:00:00Z"}"""
        val created = SubLoungeEventReducer.reduce(
            snapshot(),
            event("RECOMMENDATION_CARD_CREATED", cardJson),
        )!!
        val reacted = SubLoungeEventReducer.reduce(
            created,
            event(
                "RECOMMENDATION_CARD_REACTED",
                cardJson.replace("\"reactionCount\":0", "\"reactionCount\":4")
                    .replace("\"reactedByMe\":false", "\"reactedByMe\":true"),
            ),
        )

        assertEquals(4, reacted?.cards?.single()?.reactionCount)
        assertEquals(false, reacted?.cards?.single()?.reactedByMe)
    }

    @Test
    fun `card deletion removes matching recommendation`() {
        val cardJson = """{"id":"card","subLoungeId":"room","clientCardId":"client","senderAlias":"Mina","trackTitle":"Song","artistName":"Artist","reactionCount":0,"reactedByMe":false,"canDelete":true,"createdAt":"2026-07-12T02:00:00Z"}"""
        val created = SubLoungeEventReducer.reduce(
            snapshot(),
            event("RECOMMENDATION_CARD_CREATED", cardJson),
        )!!

        val deleted = SubLoungeEventReducer.reduce(
            created,
            event("RECOMMENDATION_CARD_DELETED", """{"cardId":"card","subLoungeId":"room"}"""),
        )

        assertTrue(deleted!!.cards.isEmpty())
    }

    @Test
    fun `poll event preserves personal vote`() {
        val reduced = SubLoungeEventReducer.reduce(
            snapshot(),
            event(
                "LOUNGE_POLL_UPDATED",
                """{"options":[{"key":"CHILL","voteCount":5}],"myVote":"ENERGY"}""",
            ),
        )

        assertEquals(5, reduced?.poll?.options?.single()?.voteCount)
        assertEquals("CHILL", reduced?.poll?.myVote)
    }

    private fun snapshot() = SubLoungeSnapshotDto(
        id = "room",
        buildingLoungeId = "building",
        title = "실제 방",
        style = null,
        memberCount = 1,
        joined = true,
        listeningStatuses = emptyList(),
        cards = emptyList(),
        poll = LoungePollStateDto(
            listOf(LoungePollOptionDto("CHILL", 0)),
            myVote = "CHILL",
        ),
        generatedAt = "2026-07-12T01:00:00Z",
    )

    private fun event(type: String, payload: String) = RealtimeEvent.SubLoungeUpdated(
        destination = "/topic/sub-lounges/room",
        envelope = RealtimeEventEnvelope(
            eventId = "event-$type",
            type = type,
            version = 1,
            timestamp = "2026-07-12T02:00:00Z",
            payload = JsonParser.parseString(payload),
        ),
    )
}
