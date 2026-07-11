package com.melodybubble.server.nearby

import com.melodybubble.server.realtime.RealtimeEventTypes
import com.melodybubble.server.realtime.RealtimePublisher
import com.melodybubble.server.realtime.RealtimeQueues
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SendNearbyReactionRequest(
    val clientReactionId: String,
    val reactionType: String,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
)

data class NearbyReactionResponse(
    val reactionId: UUID,
    val clientReactionId: UUID,
    val reactionType: String,
    val createdAt: Instant,
)

data class NearbyReactionCreatedPayload(
    val reactionId: UUID,
    val clientReactionId: UUID,
    val reactionType: String,
    val senderAlias: String,
    val trackTitle: String?,
    val trackArtist: String?,
    val createdAt: Instant,
)

private data class ReactionRecipient(val userId: UUID, val allowReactions: Boolean)

private data class StoredReaction(
    val reactionId: UUID,
    val senderId: UUID,
    val recipientId: UUID,
    val clientReactionId: UUID,
    val reactionType: String,
    val trackTitle: String?,
    val trackArtist: String?,
    val createdAt: Instant,
) {
    fun response() = NearbyReactionResponse(reactionId, clientReactionId, reactionType, createdAt)
}

@Service
class NearbyReactionService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val realtime: RealtimePublisher,
    private val nearby: NearbyService,
) {
    fun received(recipientId: UUID, limit: Int): List<NearbyReactionCreatedPayload> {
        rateLimiter.enforce(recipientId, "REACTION_INBOX", 60, Duration.ofMinutes(1))
        return jdbc.query(
            """
            select reaction.id,reaction.client_reaction_id,reaction.reaction_type,
              sender.display_name,reaction.track_title,reaction.track_artist,reaction.created_at
            from nearby_reactions reaction
            join users sender on sender.id=reaction.sender_id
            where reaction.recipient_id=?
            order by reaction.created_at desc,reaction.id desc
            limit ?
            """.trimIndent(),
            { rs, _ ->
                NearbyReactionCreatedPayload(
                    reactionId = UUID.fromString(rs.getString("id")),
                    clientReactionId = UUID.fromString(rs.getString("client_reaction_id")),
                    reactionType = rs.getString("reaction_type"),
                    senderAlias = rs.getString("display_name"),
                    trackTitle = rs.getString("track_title"),
                    trackArtist = rs.getString("track_artist"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
