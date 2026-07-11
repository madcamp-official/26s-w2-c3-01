package com.example.myapplication.ui

import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.realtime.RealtimeEventEnvelope
import com.example.myapplication.data.remote.LoungePollOptionDto
import com.example.myapplication.data.remote.LoungePollStateDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubLoungeEventReducerTest {
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
