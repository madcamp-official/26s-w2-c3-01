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
            recipientId,
            limit.coerceIn(1, 100),
        )
    }

    @Transactional
    fun send(senderId: UUID, nearbyHandle: String, request: SendNearbyReactionRequest): NearbyReactionResponse {
        val clientReactionId = runCatching { UUID.fromString(request.clientReactionId) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 clientReactionId가 필요합니다.")
        }
        val reactionType = request.reactionType.trim().uppercase()
        if (reactionType !in REACTION_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 리액션입니다.")
        }
        val (trackTitle, trackArtist) = validatedTrack(request.trackTitle, request.trackArtist)
        val recipient = resolveActiveRecipient(senderId, nearbyHandle)
        if (recipient.userId == senderId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신에게 리액션을 보낼 수 없습니다.")
        }
        if (!recipient.allowReactions) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "상대방이 음악 리액션을 받지 않습니다.")
        }
        ensureNotBlocked(senderId, recipient.userId)
        rateLimiter.enforce(senderId, "NEARBY_REACTION", 20, Duration.ofMinutes(1))

        val reactionId = UUID.randomUUID()
        val inserted = jdbc.update(
            """
            insert into nearby_reactions(
              id,sender_id,recipient_id,client_reaction_id,reaction_type,track_title,track_artist
            ) values (?,?,?,?,?,?,?)
            on conflict(sender_id,client_reaction_id) do nothing
            """.trimIndent(),
            reactionId,
            senderId,
            recipient.userId,
            clientReactionId,
            reactionType,
            trackTitle,
            trackArtist,
        )
        val stored = load(senderId, clientReactionId)
        if (stored.recipientId != recipient.userId ||
            stored.reactionType != reactionType ||
            stored.trackTitle != trackTitle ||
            stored.trackArtist != trackArtist
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "clientReactionId가 이미 다른 리액션에 사용되었습니다.",
            )
        }
        if (inserted == 1) {
            publishCreated(stored)
            nearby.publishPopularTracksAroundAfterCommit(stored.recipientId)
        }
        return stored.response()
    }

    private fun publishCreated(reaction: StoredReaction) {
        val senderAlias = jdbc.queryForObject(
            "select display_name from users where id=?",
            String::class.java,
            reaction.senderId,
        ) ?: "Listener"
        realtime.toUserAfterCommit(
            reaction.recipientId,
            RealtimeQueues.REACTIONS,
            RealtimeEventTypes.NEARBY_REACTION_CREATED,
            NearbyReactionCreatedPayload(
                reactionId = reaction.reactionId,
                clientReactionId = reaction.clientReactionId,
                reactionType = reaction.reactionType,
                senderAlias = senderAlias,
                trackTitle = reaction.trackTitle,
                trackArtist = reaction.trackArtist,
                createdAt = reaction.createdAt,
            ),
        )
    }

    private fun load(senderId: UUID, clientReactionId: UUID): StoredReaction = jdbc.query(
        """
        select id,sender_id,recipient_id,client_reaction_id,reaction_type,track_title,track_artist,created_at
        from nearby_reactions where sender_id=? and client_reaction_id=?
        """.trimIndent(),
        { rs, _ ->
            StoredReaction(
                reactionId = UUID.fromString(rs.getString("id")),
                senderId = UUID.fromString(rs.getString("sender_id")),
                recipientId = UUID.fromString(rs.getString("recipient_id")),
                clientReactionId = UUID.fromString(rs.getString("client_reaction_id")),
