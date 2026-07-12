package com.melodybubble.server.social

import com.melodybubble.server.chat.ChatService
import com.melodybubble.server.profile.ProfileMediaStorage
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class FollowResponse(
    val following: Boolean,
    val mutual: Boolean,
    val relationship: String,
    val roomId: UUID?,
    val peerAlias: String,
    val peerColor: String,
    val updatedAt: Instant = Instant.now(),
)

data class BlockedUser(
    val blockId: UUID,
    val displayAlias: String,
    val profileColor: String,
    val blockedAt: Instant,
)

data class SocialConnection(
    val relationshipId: UUID?,
    val displayAlias: String,
    val profileColor: String,
    val avatarUrl: String?,
    val bio: String,
    val mutual: Boolean,
    val followedAt: Instant,
)

data class ReportRequest(val requestId: String, val reason: String, val description: String? = null)
data class ReportResponse(val reportId: UUID, val status: String = "SUBMITTED", val submittedAt: Instant = Instant.now())

private data class Peer(val id: UUID, val alias: String, val color: String)

@Service
class SocialService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val chat: ChatService,
    private val media: ProfileMediaStorage,
) {
    @Transactional
    fun follow(userId: UUID, handle: String): FollowResponse {
        rateLimiter.enforce(userId, "FOLLOW", 20, Duration.ofMinutes(1))
        val peer = resolveActivePeer(userId, handle)
        ensureNotBlocked(userId, peer.id)
        jdbc.update(
            "insert into user_follows(follower_id,followed_id) values (?,?) on conflict do nothing",
            userId,
            peer.id,
        )
        return followState(userId, peer)
    }

    @Transactional
    fun unfollow(userId: UUID, handle: String): FollowResponse {
        rateLimiter.enforce(userId, "FOLLOW", 20, Duration.ofMinutes(1))
        val peer = resolveActivePeer(userId, handle)
        jdbc.update("delete from user_follows where follower_id=? and followed_id=?", userId, peer.id)
        return followState(userId, peer)
    }

    @Transactional
    fun block(userId: UUID, handle: String): BlockedUser {
        rateLimiter.enforce(userId, "BLOCK", 20, Duration.ofHours(1))
        val peer = resolveActivePeer(userId, handle)
        val blockId = jdbc.query(
            """
            insert into user_blocks(id,blocker_id,blocked_id) values (?,?,?)
            on conflict(blocker_id,blocked_id) do update set created_at=user_blocks.created_at
            returning id
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            UUID.randomUUID(),
            userId,
            peer.id,
        ).single()
        jdbc.update(
            "delete from user_follows where (follower_id=? and followed_id=?) or (follower_id=? and followed_id=?)",
            userId,
            peer.id,
            peer.id,
            userId,
        )
        return blockedUser(userId, blockId)
    }

    fun blocks(userId: UUID): List<BlockedUser> = jdbc.query(
        """
        select b.id,u.display_name,u.profile_color,b.created_at
        from user_blocks b join users u on u.id=b.blocked_id
        where b.blocker_id=? order by b.created_at desc
        """.trimIndent(),
        { rs, _ ->
            BlockedUser(
                UUID.fromString(rs.getString(1)),
                rs.getString(2),
                rs.getString(3),
                rs.getTimestamp(4).toInstant(),
            )
        },
        userId,
    )

    fun following(userId: UUID): List<SocialConnection> = jdbc.query(
        """
        select mine.id,u.display_name,u.profile_color,u.avatar_data_url,u.avatar_object_key,u.bio,
          exists(select 1 from user_follows back where back.follower_id=u.id and back.followed_id=?),
          mine.created_at
        from user_follows mine join users u on u.id=mine.followed_id
        where mine.follower_id=?
          and not exists(select 1 from user_blocks b where
            (b.blocker_id=? and b.blocked_id=u.id) or (b.blocker_id=u.id and b.blocked_id=?))
        order by mine.created_at desc
        """.trimIndent(),
        ::connection,
        userId, userId, userId, userId,
    )

    fun followers(userId: UUID): List<SocialConnection> = jdbc.query(
        """
        select mine.id,u.display_name,u.profile_color,u.avatar_data_url,u.avatar_object_key,u.bio,
          (mine.id is not null),incoming.created_at
        from user_follows incoming join users u on u.id=incoming.follower_id
        left join user_follows mine on mine.follower_id=? and mine.followed_id=u.id
        where incoming.followed_id=?
          and not exists(select 1 from user_blocks b where
            (b.blocker_id=? and b.blocked_id=u.id) or (b.blocker_id=u.id and b.blocked_id=?))
        order by incoming.created_at desc
        """.trimIndent(),
        ::connection,
        userId, userId, userId, userId,
    )

    @Transactional
    fun unfollowRelationship(userId: UUID, relationshipId: UUID) {
        rateLimiter.enforce(userId, "FOLLOW", 20, Duration.ofMinutes(1))
        val deleted = jdbc.update(
            "delete from user_follows where id=? and follower_id=?",
            relationshipId,
            userId,
        )
        if (deleted == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "팔로우 관계를 찾을 수 없습니다.")
    }

    @Transactional
    fun unblock(userId: UUID, blockId: UUID) {
        val deleted = jdbc.update("delete from user_blocks where id=? and blocker_id=?", blockId, userId)
        if (deleted == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "차단 내역을 찾을 수 없습니다.")
    }

    @Transactional
    fun report(userId: UUID, handle: String, request: ReportRequest): ReportResponse {
        rateLimiter.enforce(userId, "REPORT", 5, Duration.ofHours(1))
        val peer = resolveActivePeer(userId, handle)
        val requestId = runCatching { UUID.fromString(request.requestId) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 requestId가 필요합니다.")
        }
        val reason = request.reason.trim().uppercase()
        if (reason !in REPORT_REASONS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 신고 사유입니다.")
        }
        val description = request.description?.trim()?.take(500)?.ifBlank { null }
        val reportId = UUID.randomUUID()
        jdbc.update(
            """
            insert into user_reports(id,reporter_id,reported_id,request_id,reason,description)
            values (?,?,?,?,?,?) on conflict(reporter_id,request_id) do nothing
            """.trimIndent(),
            reportId,
            userId,
            peer.id,
            requestId,
            reason,
            description,
        )
        val storedId = jdbc.query(
            "select id from user_reports where reporter_id=? and request_id=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            userId,
            requestId,
        ).single()
        return ReportResponse(storedId)
    }

    private fun followState(userId: UUID, peer: Peer): FollowResponse {
        val following = follows(userId, peer.id)
        val followsMe = follows(peer.id, userId)
        val mutual = following && followsMe
        val roomId = if (mutual) ensureDirectRoom(userId, peer.id) else null
        val relationship = when {
            mutual -> "MUTUAL"
            following -> "FOLLOWING"
            followsMe -> "FOLLOWS_ME"
            else -> "NONE"
        }
        return FollowResponse(following, mutual, relationship, roomId, peer.alias, peer.color)
    }

    private fun ensureDirectRoom(left: UUID, right: UUID): UUID {
        val (first, second) = listOf(left, right).sortedBy(UUID::toString)
        val roomId = UUID.nameUUIDFromBytes("melody-direct:$first:$second".toByteArray(StandardCharsets.UTF_8))
        jdbc.update("insert into chat_rooms(id,type) values (?,'DIRECT') on conflict(id) do nothing", roomId)
        val pairInserted = jdbc.update(
            "insert into direct_chat_pairs(first_user_id,second_user_id,room_id) values (?,?,?) on conflict do nothing",
            first,
            second,
            roomId,
        )
        jdbc.update(
            "insert into chat_room_members(room_id,user_id) values (?,?),(?,?) on conflict do nothing",
            roomId,
            first,
            roomId,
            second,
        )
        if (pairInserted == 1) chat.publishRoomCreated(roomId)
        return roomId
    }

    private fun follows(from: UUID, to: UUID): Boolean = jdbc.queryForObject(
        "select exists(select 1 from user_follows where follower_id=? and followed_id=?)",
        Boolean::class.java,
        from,
        to,
    ) == true

    private fun ensureNotBlocked(userId: UUID, peerId: UUID) {
        val blocked = jdbc.queryForObject(
            """
            select exists(select 1 from user_blocks
              where (blocker_id=? and blocked_id=?) or (blocker_id=? and blocked_id=?))
            """.trimIndent(),
            Boolean::class.java,
            userId,
            peerId,
            peerId,
            userId,
        ) == true
        if (blocked) throw ResponseStatusException(HttpStatus.FORBIDDEN, "차단 관계에서는 요청할 수 없습니다.")
    }

    private fun resolveActivePeer(userId: UUID, handle: String): Peer {
        if (!handle.matches(Regex("[A-Za-z0-9_]{3,64}"))) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "주변 사용자를 찾을 수 없습니다.")
        }
        return jdbc.query(
            """
            select u.id,u.display_name,u.profile_color
            from presence_sessions ps join users u on u.id=ps.user_id
            where ps.nearby_handle=? and ps.expires_at>now() and ps.user_id<>?
            """.trimIndent(),
            { rs, _ -> Peer(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)) },
            handle,
            userId,
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "주변 사용자가 더 이상 활성 상태가 아닙니다.")
    }

    private fun blockedUser(userId: UUID, blockId: UUID): BlockedUser = blocks(userId)
        .firstOrNull { it.blockId == blockId }
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "차단 내역을 찾을 수 없습니다.")

    private fun connection(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = SocialConnection(
        relationshipId = rs.getString(1)?.let(UUID::fromString),
        displayAlias = rs.getString(2),
        profileColor = rs.getString(3),
        avatarUrl = media.signedUrl(rs.getString(5)) ?: rs.getString(4),
        bio = rs.getString(6).orEmpty(),
        mutual = rs.getBoolean(7),
        followedAt = rs.getTimestamp(8).toInstant(),
    )

    companion object {
        private val REPORT_REASONS = setOf(
            "SPAM", "HARASSMENT", "HATE", "SEXUAL_CONTENT", "IMPERSONATION", "OTHER",
        )
    }
}

@RestController
@RequestMapping("/api/v1")
class SocialController(private val social: SocialService) {
    @PutMapping("/nearby/{handle}/follow")
    fun follow(principal: Principal, @PathVariable handle: String) =
        social.follow(UUID.fromString(principal.name), handle)

    @DeleteMapping("/nearby/{handle}/follow")
    fun unfollow(principal: Principal, @PathVariable handle: String) =
        social.unfollow(UUID.fromString(principal.name), handle)

    @GetMapping("/me/following")
    fun following(principal: Principal) = social.following(UUID.fromString(principal.name))

    @GetMapping("/me/followers")
    fun followers(principal: Principal) = social.followers(UUID.fromString(principal.name))

    @DeleteMapping("/me/following/{relationshipId}")
    fun unfollowRelationship(principal: Principal, @PathVariable relationshipId: UUID) =
        social.unfollowRelationship(UUID.fromString(principal.name), relationshipId)

    @PutMapping("/nearby/{handle}/block")
    fun block(principal: Principal, @PathVariable handle: String) =
        social.block(UUID.fromString(principal.name), handle)

    @GetMapping("/me/blocks")
    fun blocks(principal: Principal) = social.blocks(UUID.fromString(principal.name))

    @DeleteMapping("/me/blocks/{blockId}")
    fun unblock(principal: Principal, @PathVariable blockId: UUID) =
        social.unblock(UUID.fromString(principal.name), blockId)

    @PostMapping("/nearby/{handle}/reports")
    fun report(principal: Principal, @PathVariable handle: String, @RequestBody request: ReportRequest) =
        social.report(UUID.fromString(principal.name), handle, request)
}
