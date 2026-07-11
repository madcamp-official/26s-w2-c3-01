package com.melodybubble.server.realtime

import com.melodybubble.server.auth.JwtSession
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Stable wire format used by every user-specific STOMP queue.
 *
 * Payloads are deliberately versioned independently from the REST responses so clients can
 * ignore unknown fields/events and recover their authoritative state through REST after a
 * reconnect.
 */
data class RealtimeEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),
    val type: String,
    val version: Int = 1,
    val timestamp: Instant = Instant.now(),
    val payload: T,
)

object RealtimeQueues {
    const val CHAT = "/queue/chat"
    const val REACTIONS = "/queue/reactions"
    const val NEARBY = "/queue/nearby"
    const val NOTIFICATIONS = "/queue/notifications"
    const val ERRORS = "/queue/errors"
    const val ACK = "/queue/ack"
}

object RealtimeEventTypes {
    const val CHAT_ROOM_CREATED = "CHAT_ROOM_CREATED"
    const val CHAT_MESSAGE_CREATED = "CHAT_MESSAGE_CREATED"
    const val CHAT_MESSAGE_READ = "CHAT_MESSAGE_READ"
    const val CHAT_ROOM_UPDATED = "CHAT_ROOM_UPDATED"
    const val NEARBY_REACTION_CREATED = "NEARBY_REACTION_CREATED"
    const val NEARBY_SNAPSHOT = "NEARBY_SNAPSHOT"
    const val NEARBY_MUSIC_UPDATED = "NEARBY_MUSIC_UPDATED"
    const val POPULAR_TRACKS_UPDATED = "POPULAR_TRACKS_UPDATED"
    const val NOTIFICATION_CREATED = "NOTIFICATION_CREATED"
    const val ACK = "ACK"
    const val ERROR = "ERROR"
}

@org.springframework.stereotype.Component
class RealtimeSessionPolicy {
    private val revokedTokens = ConcurrentHashMap<String, Instant>()

    fun isAllowed(session: JwtSession, now: Instant = Instant.now()): Boolean {
        revokedTokens.entries.removeIf { (_, expiresAt) -> !expiresAt.isAfter(now) }
        return session.expiresAt.isAfter(now) && !revokedTokens.containsKey(session.tokenId)
    }

    fun revoke(session: JwtSession) {
        revokedTokens[session.tokenId] = session.expiresAt
    }
}

/**
 * Sends realtime messages only after the surrounding database transaction commits.
 *
 * Calling this outside a transaction remains useful for read-only acknowledgements and sends
 * immediately. Delivery failures after commit are logged: the REST response continues to
 * represent the already committed write and clients recover through REST on reconnect.
 */
@Service
class RealtimePublisher(private val messaging: SimpMessagingTemplate) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : Any> toUserAfterCommit(userId: UUID, destination: String, type: String, payload: T) {
        val envelope = RealtimeEnvelope(type = type, payload = payload)
        afterCommit {
            runCatching {
                messaging.convertAndSendToUser(userId.toString(), destination, envelope)
            }.onFailure { error ->
                logger.warn(
                    "Realtime delivery failed after commit: userId={}, destination={}, type={}",
                    userId,
                    destination,
                    type,
                    error,
