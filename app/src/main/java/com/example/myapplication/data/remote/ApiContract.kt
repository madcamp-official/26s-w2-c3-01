package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig

data class ApiEnvironment(
    val apiBaseUrl: String = BuildConfig.API_BASE_URL.trim(),
    val stompWsUrl: String = BuildConfig.STOMP_WS_URL.trim()
) {
    val isConfigured: Boolean
        get() = apiBaseUrl.startsWith("https://") && stompWsUrl.startsWith("wss://")
}

/**
 * Single source of truth for the server contract described in the planning draft.
 * Nearby details are addressed through expiring handles; a user UUID, coordinates,
 * exact distance, and direction are intentionally absent from client-facing models.
 */
object MelodyApiContract {
    object Rest {
        const val LOGIN = "/api/v1/auth/login"
        const val REFRESH = "/api/v1/auth/refresh"
        const val ME = "/api/v1/me"
        const val PRIVACY = "/api/v1/me/privacy"
        const val NEARBY_SNAPSHOT = "/api/v1/nearby/snapshot"
        const val NEARBY_DETAIL = "/api/v1/nearby/{nearbyHandle}"
        const val LOUNGES = "/api/v1/rooms"
        const val LOUNGE_DETAIL = "/api/v1/rooms/{roomId}"
        const val CHAT_ROOMS = "/api/v1/chat/rooms"
        const val CHAT_HISTORY = "/api/v1/chat/rooms/{roomId}/messages"
        const val NOTIFICATIONS = "/api/v1/notifications"
        const val OFFLINE_SYNC = "/api/v1/offline-exchanges/sync"
    }

    object Send {
        const val PRESENCE_START = "/app/presence/start"
        const val PRESENCE_STOP = "/app/presence/stop"
        const val PRESENCE_HEARTBEAT = "/app/presence/heartbeat"
        const val LOCATION_UPDATE = "/app/location/update"
        const val MUSIC_UPDATE = "/app/music/update"
        const val REACTION = "/app/reaction/send"
        const val CHAT = "/app/chat/send"
        const val CHAT_READ = "/app/chat/read"
        const val ROOM_JOIN = "/app/room/join"
        const val ROOM_LEAVE = "/app/room/leave"
        const val ROOM_CARD = "/app/room/card"
        const val ROOM_REACTION = "/app/room/reaction"
        const val ROOM_VOTE = "/app/room/vote"
    }

    object Subscribe {
        const val NEARBY = "/user/queue/nearby"
        const val CHAT = "/user/queue/chat"
        const val NOTIFICATIONS = "/user/queue/notifications"
        const val REACTIONS = "/user/queue/reactions"
        const val ACK = "/user/queue/ack"
        const val ERRORS = "/user/queue/errors"

        fun roomState(roomId: String) = "/topic/room/$roomId/state"
        fun roomCards(roomId: String) = "/topic/room/$roomId/cards"
        fun roomVotes(roomId: String) = "/topic/room/$roomId/votes"
    }
}

data class EventEnvelope<T>(
    val eventId: String,
    val requestId: String?,
    val type: String,
    val version: Int,
    val sequence: Long?,
    val timestamp: String,
    val payload: T
)
