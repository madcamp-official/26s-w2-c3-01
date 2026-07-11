package com.example.myapplication.data.realtime

import com.google.gson.JsonElement

object RealtimeDestinations {
    const val CHAT = "/user/queue/chat"
    const val REACTIONS = "/user/queue/reactions"
    const val NEARBY = "/user/queue/nearby"
    const val NOTIFICATIONS = "/user/queue/notifications"
    const val ERRORS = "/user/queue/errors"

    val userQueues = listOf(CHAT, REACTIONS, NEARBY, NOTIFICATIONS, ERRORS)
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
    val reactionType: String? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val createdAt: String? = null,
)

data class RealtimeTrackPayload(
    val title: String? = null,
    val artist: String? = null,
)

data class NearbyMusicUpdatedPayload(
    val nearbyHandle: String? = null,
    val isPlaying: Boolean? = null,
    val track: RealtimeTrackPayload? = null,
)

data class PopularTrackPayload(
    val title: String? = null,
    val artist: String? = null,
    val listenerCount: Int? = null,
    val reactionCount: Int? = null,
)

data class PopularTracksUpdatedPayload(
    val tracks: List<PopularTrackPayload> = emptyList(),
)
