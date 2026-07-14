package com.melodybubble.server.nearby

import com.melodybubble.server.profile.AvatarUrlFactory
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
    val senderProfileHandle: String,
    val senderAvatarUrl: String,
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
    private val avatars: AvatarUrlFactory,
) {
    fun received(recipientId: UUID, limit: Int): List<NearbyReactionCreatedPayload> {
        rateLimiter.enforce(recipientId, "REACTION_INBOX", 60, Duration.ofMinutes(1))
        return jdbc.query(
            """
            select reaction.id,reaction.client_reaction_id,reaction.reaction_type,
              sender.display_name,sender.profile_handle,sender.avatar_seed,sender.avatar_data_url,
              reaction.track_title,reaction.track_artist,reaction.created_at
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
                    senderProfileHandle = rs.getString("profile_handle"),
                    senderAvatarUrl = avatars.resolve(rs.getString("avatar_seed"), rs.getString("avatar_data_url")),
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
        val sender = jdbc.queryForMap(
            "select display_name,profile_handle,avatar_seed,avatar_data_url from users where id=?",
            reaction.senderId,
        )
        realtime.toUserAfterCommit(
            reaction.recipientId,
            RealtimeQueues.REACTIONS,
            RealtimeEventTypes.NEARBY_REACTION_CREATED,
            NearbyReactionCreatedPayload(
                reactionId = reaction.reactionId,
                clientReactionId = reaction.clientReactionId,
                reactionType = reaction.reactionType,
                senderAlias = sender["display_name"] as? String ?: "Listener",
                senderProfileHandle = sender["profile_handle"] as String,
                senderAvatarUrl = avatars.resolve(
                    sender["avatar_seed"] as String,
                    sender["avatar_data_url"] as? String,
                ),
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
                reactionType = rs.getString("reaction_type"),
                trackTitle = rs.getString("track_title"),
                trackArtist = rs.getString("track_artist"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        },
        senderId,
        clientReactionId,
    ).single()

    private fun resolveActiveRecipient(senderId: UUID, nearbyHandle: String): ReactionRecipient {
        if (!nearbyHandle.matches(Regex("[A-Za-z0-9_]{3,48}"))) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "주변 사용자를 찾을 수 없습니다.")
        }
        return jdbc.query(
            """
            select ps.user_id,privacy.allow_reactions
            from presence_sessions ps
            join user_privacy_settings privacy on privacy.user_id=ps.user_id
            join current_locations recipient_location
              on recipient_location.session_id=ps.id and recipient_location.expires_at>now()
            where ps.nearby_handle=? and ps.expires_at>now()
              and privacy.discoverable=true
              and privacy.discoverability_scope<>'HIDDEN'
              and (
                privacy.discoverability_scope='NEARBY' or (
                  privacy.discoverability_scope='MUTUALS'
                  and exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=ps.user_id)
                  and exists(select 1 from user_follows f where f.follower_id=ps.user_id and f.followed_id=?)
                )
              )
              and exists(
                select 1 from presence_sessions sender_session
                join current_locations sender_location
                  on sender_location.session_id=sender_session.id and sender_location.expires_at>now()
                where sender_session.user_id=? and sender_session.expires_at>now()
                  and ST_DWithin(
                    recipient_location.point::geography,
                    sender_location.point::geography,
                    least(
                      (select discovery_radius_meters from user_privacy_settings where user_id=?),
                      15
                    )
                  )
              )
            """.trimIndent(),
            { rs, _ -> ReactionRecipient(UUID.fromString(rs.getString(1)), rs.getBoolean(2)) },
            nearbyHandle,
            senderId,
            senderId,
            senderId,
            senderId,
        ).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "주변 사용자가 더 이상 활성 상태가 아닙니다.")
    }

    private fun ensureNotBlocked(senderId: UUID, recipientId: UUID) {
        val blocked = jdbc.queryForObject(
            """
            select exists(select 1 from user_blocks
              where (blocker_id=? and blocked_id=?) or (blocker_id=? and blocked_id=?))
            """.trimIndent(),
            Boolean::class.java,
            senderId,
            recipientId,
            recipientId,
            senderId,
        ) == true
        if (blocked) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "차단 관계에서는 리액션을 보낼 수 없습니다.")
        }
    }

    private fun validatedTrack(rawTitle: String?, rawArtist: String?): Pair<String?, String?> {
        val title = rawTitle?.trim()?.ifBlank { null }
        val artist = rawArtist?.trim()?.ifBlank { null }
        if ((title == null) != (artist == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "곡 제목과 아티스트를 함께 입력해 주세요.")
        }
        if ((title?.length ?: 0) > 160 || (artist?.length ?: 0) > 160) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "곡 제목과 아티스트는 160자까지 입력할 수 있습니다.")
        }
        return title to artist
    }

    companion object {
        private val REACTION_TYPES = setOf("LIKE", "SAME_TASTE", "GREAT_PICK", "LISTEN_TOGETHER")
    }
}

@RestController
@RequestMapping("/api/v1/nearby")
class NearbyReactionController(private val reactions: NearbyReactionService) {
    @GetMapping("/reactions")
    fun received(
        principal: Principal,
        @RequestParam(defaultValue = "100") limit: Int,
    ) = reactions.received(UUID.fromString(principal.name), limit)

    @PostMapping("/{nearbyHandle}/reactions")
    fun send(
        principal: Principal,
        @PathVariable nearbyHandle: String,
        @RequestBody request: SendNearbyReactionRequest,
    ) = reactions.send(UUID.fromString(principal.name), nearbyHandle, request)
}
