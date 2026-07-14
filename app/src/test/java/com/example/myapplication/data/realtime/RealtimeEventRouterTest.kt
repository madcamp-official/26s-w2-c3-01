package com.example.myapplication.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeEventRouterTest {
    private val router = RealtimeEventRouter()

    @Test
    fun routesChatMessageEnvelope() {
        val event = router.route(
            RealtimeDestinations.CHAT,
            """{
              "eventId":"event-1",
              "type":"CHAT_MESSAGE_CREATED",
              "version":1,
              "timestamp":"2026-07-11T20:00:00Z",
              "payload":{
                "messageId":"message-1",
                "clientMessageId":"client-1",
                "roomId":"room-1",
                "senderAlias":"Lime",
                "content":"안녕하세요",
                "sentAt":"2026-07-11T20:00:00Z",
                "isMine":true
              }
            }""".trimIndent(),
        )

        assertTrue(event is RealtimeEvent.ChatMessageCreated)
        val payload = (event as RealtimeEvent.ChatMessageCreated).envelope.payload
        assertEquals("message-1", payload.messageId)
        assertEquals("client-1", payload.clientMessageId)
        assertEquals(true, payload.isMine)
    }

    @Test
    fun routesPopularTracksWithoutRequiringOptionalFields() {
        val event = router.route(
            RealtimeDestinations.NEARBY,
            """{
              "eventId":"event-2",
              "type":"POPULAR_TRACKS_UPDATED",
              "version":1,
              "timestamp":"2026-07-11T20:00:00Z",
              "payload":{"tracks":[{
                "title":"Blue Night",
                "artist":"Wave",
                "artworkUrl":"https://example.com/blue-night.jpg",
                "listenerCount":3,
                "reactionCount":5
              }]}
            }""".trimIndent(),
        )

        assertTrue(event is RealtimeEvent.PopularTracksUpdated)
        val track = (event as RealtimeEvent.PopularTracksUpdated).envelope.payload.tracks.single()
        assertEquals(3, track.listenerCount)
        assertEquals(5, track.reactionCount)
        assertEquals("https://example.com/blue-night.jpg", track.artworkUrl)
    }

    @Test
    fun routesAuthoritativeNearbySnapshotWithoutExactDistance() {
        val event = router.route(
            RealtimeDestinations.NEARBY,
            """{
              "eventId":"nearby-snapshot-1",
              "type":"NEARBY_SNAPSHOT",
              "version":1,
              "timestamp":"2026-07-13T12:00:00Z",
              "payload":{
                "generatedAt":"2026-07-13T12:00:00Z",
                "radiusMeters":15,
                "items":[{
                  "nearbyHandle":"n_opaque",
                  "profileHandle":"p_public",
                  "displayAlias":"Mint",
                  "profileColor":"#25C76F",
                  "displayPosition":{"x":0.6,"y":0.5},
                  "matchScore":80,
                  "proximity":"WITHIN_5M",
                  "relationship":"NONE",
                  "canReact":true,
                  "track":null
                }]
              }
            }""".trimIndent(),
        )

        assertTrue(event is RealtimeEvent.NearbySnapshot)
        val snapshot = (event as RealtimeEvent.NearbySnapshot).envelope.payload
        assertEquals(15, snapshot.radiusMeters)
        assertEquals("WITHIN_5M", snapshot.items.single().proximity)
    }

    @Test
    fun malformedEnvelopeBecomesParsingError() {
        val event = router.route(RealtimeDestinations.CHAT, "{\"type\":\"CHAT_MESSAGE_CREATED\"}")
        assertTrue(event is RealtimeEvent.ParsingError)
    }

    @Test
    fun unknownVersionedEventRemainsForwardCompatible() {
        val event = router.route(
            RealtimeDestinations.NOTIFICATIONS,
            """{
              "eventId":"event-3",
              "type":"A_FUTURE_EVENT",
              "version":2,
              "timestamp":"2026-07-11T20:00:00Z",
              "payload":{"newField":true}
            }""".trimIndent(),
        )
        assertTrue(event is RealtimeEvent.Unknown)
        assertEquals("event-3", event.eventId)
    }

    @Test
    fun loungeTopicEventIsRoutedForFeatureSpecificHandling() {
        val destination = RealtimeDestinations.subLounge("11111111-1111-4111-8111-111111111111")
        val event = router.route(
            destination,
            """{
              "eventId":"lounge-event-1",
              "type":"RECOMMENDATION_CARD_CREATED",
              "version":1,
              "timestamp":"2026-07-12T01:00:00Z",
              "payload":{"id":"card-1"}
            }""".trimIndent(),
        )

        assertTrue(event is RealtimeEvent.SubLoungeUpdated)
        assertEquals(destination, event.destination)
        assertEquals("lounge-event-1", event.eventId)
    }

    @Test
    fun locationLoungeTopicEventTriggersMapSpecificHandling() {
        val event = router.route(
            RealtimeDestinations.LOCATION_LOUNGES,
            """{
              "eventId":"location-lounge-event-1",
              "type":"LOUNGE_RADIUS_CHANGED",
              "version":1,
              "timestamp":"2026-07-14T01:00:00Z",
              "payload":{"loungeId":"11111111-1111-4111-8111-111111111111","radius":10}
            }""".trimIndent(),
        )

        assertTrue(event is RealtimeEvent.LocationLoungeUpdated)
        assertEquals(RealtimeDestinations.LOCATION_LOUNGES, event.destination)
    }

    @Test
    fun retryScheduleUsesRequiredBackoffAndCapsAtThirtySeconds() {
        assertEquals(1_000L, StompRealtimeClient.retryDelay(1))
        assertEquals(2_000L, StompRealtimeClient.retryDelay(2))
        assertEquals(5_000L, StompRealtimeClient.retryDelay(3))
        assertEquals(10_000L, StompRealtimeClient.retryDelay(4))
        assertEquals(20_000L, StompRealtimeClient.retryDelay(5))
        assertEquals(30_000L, StompRealtimeClient.retryDelay(6))
        assertEquals(30_000L, StompRealtimeClient.retryDelay(100))
        assertFalse(RealtimeDestinations.userQueues.contains("/user/queue/ack"))
        assertEquals(5, RealtimeDestinations.userQueues.size)
    }
}
