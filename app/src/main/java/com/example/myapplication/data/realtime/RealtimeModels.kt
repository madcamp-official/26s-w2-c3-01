package com.example.myapplication.data.realtime

import com.example.myapplication.data.remote.RemoteNearbySnapshot
import com.google.gson.JsonElement

object RealtimeDestinations {
    const val CHAT = "/user/queue/chat"
    const val REACTIONS = "/user/queue/reactions"
    const val NEARBY = "/user/queue/nearby"
    const val NOTIFICATIONS = "/user/queue/notifications"
    const val ERRORS = "/user/queue/errors"
    const val LOCATION_LOUNGES = "/topic/location-lounges"

    val userQueues = listOf(CHAT, REACTIONS, NEARBY, NOTIFICATIONS, ERRORS)
    val defaultSubscriptions = userQueues + LOCATION_LOUNGES

    fun subLounge(subLoungeId: String) = "/topic/sub-lounges/$subLoungeId"
}

/** The server-wide realtime event shape. */
data class RealtimeEventEnvelope<T>(
    val eventId: String,
    val type: String,
    val version: Int,
    val timestamp: String,
    val payload: T,
)

data class ChatRoomCreatedPayload(
    val roomId: String? = null,
    val createdAt: String? = null,
    val participantAlias: String? = null,
    val participantProfileImageUrl: String? = null,
    val peerHandle: String? = null,
    val peerAlias: String? = null,
    val peerColor: String? = null,
)

data class ChatMessageCreatedPayload(
    val messageId: String? = null,
    val clientMessageId: String? = null,
    val roomId: String? = null,
    val senderAlias: String? = null,
    val content: String? = null,
    val sentAt: String? = null,
    val isMine: Boolean? = null,
)

data class ChatMessageReadPayload(
    val roomId: String? = null,
    val readerAlias: String? = null,
    val lastReadMessageId: String? = null,
    val readAt: String? = null,
    val isMine: Boolean? = null,
)

data class ChatRoomUpdatedPayload(
    val roomId: String? = null,
    val lastMessageId: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int? = null,
    val updatedAt: String? = null,
)

data class NearbyReactionCreatedPayload(
    val reactionId: String? = null,
    val clientReactionId: String? = null,
    val senderNearbyHandle: String? = null,
    val senderAlias: String? = null,
    val senderProfileHandle: String? = null,
    val senderAvatarUrl: String? = null,
    val reactionType: String? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val createdAt: String? = null,
)

data class RealtimeTrackPayload(
    val title: String? = null,
    val artist: String? = null,
    val albumArtUrl: String? = null,
)

data class NearbyMusicUpdatedPayload(
    val nearbyHandle: String? = null,
    val profileHandle: String? = null,
    val isPlaying: Boolean? = null,
    val track: RealtimeTrackPayload? = null,
)

data class PopularTrackPayload(
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val listenerCount: Int? = null,
    val reactionCount: Int? = null,
)

data class PopularTracksUpdatedPayload(
    val tracks: List<PopularTrackPayload> = emptyList(),
)

/**
 * Notifications deliberately retain their optional metadata because notification types can be
 * introduced independently of an app release.
 */
data class RealtimeNotificationPayload(
    val notificationId: String? = null,
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
    val metadata: JsonElement? = null,
)

data class RealtimeServerErrorPayload(
    val code: String? = null,
    val message: String? = null,
    val requestId: String? = null,
    val details: JsonElement? = null,
)

/** A routed event. Every variant keeps the STOMP destination that delivered it. */
sealed interface RealtimeEvent {
    val destination: String
    val eventId: String?
    val type: String?

    data class ChatRoomCreated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<ChatRoomCreatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class ChatMessageCreated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<ChatMessageCreatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class ChatMessageRead(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<ChatMessageReadPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class ChatRoomUpdated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<ChatRoomUpdatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class NearbyReactionCreated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<NearbyReactionCreatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class NearbyMusicUpdated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<NearbyMusicUpdatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class NearbySnapshot(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<RemoteNearbySnapshot>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class PopularTracksUpdated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<PopularTracksUpdatedPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class NotificationCreated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<RealtimeNotificationPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class SubLoungeUpdated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<JsonElement>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class LocationLoungeUpdated(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<JsonElement>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class ServerError(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<RealtimeServerErrorPayload>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    data class Unknown(
        override val destination: String,
        val envelope: RealtimeEventEnvelope<JsonElement>,
    ) : RealtimeEvent {
        override val eventId: String = envelope.eventId
        override val type: String = envelope.type
    }

    /** Parsing failures are data, not exceptions escaping into WebSocket callbacks. */
    data class ParsingError(
        override val destination: String,
        val rawBody: String,
        val reason: String,
    ) : RealtimeEvent {
        override val eventId: String? = null
        override val type: String? = null
    }
}

sealed interface RealtimeConnectionState {
    data object Disconnected : RealtimeConnectionState

    data class Connecting(
        val isReconnect: Boolean,
    ) : RealtimeConnectionState

    data class Connected(
        val sessionId: String?,
        val stompVersion: String?,
        val serverHeartbeat: StompHeartbeat,
        val connectedAtEpochMillis: Long,
        val isReconnect: Boolean,
    ) : RealtimeConnectionState

    data class Reconnecting(
        val attempt: Int,
        val retryInMillis: Long,
        val cause: String?,
    ) : RealtimeConnectionState
}

data class StompHeartbeat(
    val canSendEveryMillis: Long,
    val wantsReceiveEveryMillis: Long,
) {
    companion object {
        val Disabled = StompHeartbeat(0, 0)
    }
}

sealed interface RealtimeSyncRequest {
    /** Emitted after any retry connection succeeds, including recovery from initial failures. */
    data class FullSync(
        val connectedAtEpochMillis: Long,
        val reconnectAttempt: Int,
    ) : RealtimeSyncRequest
}
