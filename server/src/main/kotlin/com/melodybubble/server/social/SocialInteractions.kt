package com.melodybubble.server.social

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.util.UUID

data class RelationshipResponse(
    val targetHandle: String,
    val relationship: String,
    val chatRoomId: UUID? = null,
)

data class ReportUserRequest(
    val reason: String = "SAFETY",
    val detail: String? = null,
)

data class ReportUserResponse(val reportId: UUID, val status: String)

@Service
class SocialInteractionService(private val jdbc: JdbcTemplate) {
    fun follow(userId: UUID, targetHandle: String): RelationshipResponse {
        val targetUserId = resolveNearbyUser(targetHandle)
        ensureNotSelf(userId, targetUserId)
        ensureNotBlocked(userId, targetUserId)
        jdbc.update(
            """
            INSERT INTO user_follows(follower_id, followee_id)
            VALUES (?, ?)
            ON CONFLICT(follower_id, followee_id) DO NOTHING
            """.trimIndent(),
            userId,
            targetUserId,
        )
        val chatRoomId = if (isMutual(userId, targetUserId)) ensureDirectChat(userId, targetUserId) else null
        return RelationshipResponse(targetHandle, relationship(userId, targetUserId), chatRoomId)
    }

    fun unfollow(userId: UUID, targetHandle: String): RelationshipResponse {
        val targetUserId = resolveNearbyUser(targetHandle)
        ensureNotSelf(userId, targetUserId)
        jdbc.update("DELETE FROM user_follows WHERE follower_id=? AND followee_id=?", userId, targetUserId)
        return RelationshipResponse(targetHandle, relationship(userId, targetUserId))
    }

    fun block(userId: UUID, targetHandle: String): RelationshipResponse {
        val targetUserId = resolveNearbyUser(targetHandle)
        ensureNotSelf(userId, targetUserId)
        jdbc.update(
            """
            INSERT INTO user_blocks(blocker_id, blocked_id)
            VALUES (?, ?)
            ON CONFLICT(blocker_id, blocked_id) DO NOTHING
            """.trimIndent(),
            userId,
            targetUserId,
        )
        jdbc.update(
            """
            DELETE FROM user_follows
            WHERE (follower_id=? AND followee_id=?) OR (follower_id=? AND followee_id=?)
            """.trimIndent(),
            userId,
            targetUserId,
            targetUserId,
            userId,
        )
        return RelationshipResponse(targetHandle, "BLOCKED")
    }

    fun unblock(userId: UUID, targetHandle: String): RelationshipResponse {
        val targetUserId = resolveNearbyUser(targetHandle)
        ensureNotSelf(userId, targetUserId)
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?", userId, targetUserId)
        return RelationshipResponse(targetHandle, relationship(userId, targetUserId))
    }

    fun report(userId: UUID, targetHandle: String, request: ReportUserRequest): ReportUserResponse {
        val targetUserId = resolveNearbyUser(targetHandle)
        ensureNotSelf(userId, targetUserId)
        val reason = request.reason.trim().uppercase().take(80).ifBlank { "SAFETY" }
        val detail = request.detail?.trim()?.take(1000)?.ifBlank { null }
        val reportId = jdbc.query(
            """
            INSERT INTO user_reports(reporter_id, reported_user_id, reason, detail)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            userId,
            targetUserId,
            reason,
            detail,
        ).first()
        return ReportUserResponse(reportId, "OPEN")
    }

    private fun resolveNearbyUser(handle: String): UUID = jdbc.query(
        """
        SELECT user_id
        FROM presence_sessions
        WHERE nearby_handle = ? AND expires_at > now()
        ORDER BY last_seen_at DESC
        LIMIT 1
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString("user_id")) },
        handle,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Nearby user is no longer available")

    private fun ensureNotSelf(userId: UUID, targetUserId: UUID) {
        if (userId == targetUserId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot interact with yourself")
        }
    }

    private fun ensureNotBlocked(userId: UUID, targetUserId: UUID) {
        val blocked = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM user_blocks
              WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)
            )
            """.trimIndent(),
            Boolean::class.java,
            userId,
            targetUserId,
            targetUserId,
            userId,
        ) == true
        if (blocked) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Follow is not available for blocked users")
        }
    }

    private fun relationship(userId: UUID, targetUserId: UUID): String {
        val blocked = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id=? AND blocked_id=?)",
            Boolean::class.java,
            userId,
            targetUserId,
        ) == true
        if (blocked) return "BLOCKED"
        val following = follows(userId, targetUserId)
        val followsMe = follows(targetUserId, userId)
        return when {
            following && followsMe -> "MUTUAL"
            following -> "FOLLOWING"
            followsMe -> "FOLLOWS_ME"
            else -> "NONE"
        }
    }

    private fun follows(followerId: UUID, followeeId: UUID): Boolean = jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM user_follows WHERE follower_id=? AND followee_id=?)",
        Boolean::class.java,
        followerId,
        followeeId,
    ) == true

    private fun isMutual(firstUserId: UUID, secondUserId: UUID): Boolean =
        follows(firstUserId, secondUserId) && follows(secondUserId, firstUserId)

    private fun ensureDirectChat(firstUserId: UUID, secondUserId: UUID): UUID {
        val existing = jdbc.query(
            """
            SELECT crm1.room_id
            FROM chat_room_members crm1
            JOIN chat_room_members crm2 ON crm2.room_id = crm1.room_id
            JOIN chat_rooms cr ON cr.id = crm1.room_id AND cr.type = 'DIRECT'
            WHERE crm1.user_id = ? AND crm2.user_id = ?
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("room_id")) },
            firstUserId,
            secondUserId,
        ).firstOrNull()
        if (existing != null) return existing

        val roomId = jdbc.query(
            "INSERT INTO chat_rooms(id, type) VALUES (gen_random_uuid(), 'DIRECT') RETURNING id",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
        ).first()
        jdbc.update("INSERT INTO chat_room_members(room_id, user_id) VALUES (?, ?), (?, ?)", roomId, firstUserId, roomId, secondUserId)
        return roomId
    }
}

@RestController
@RequestMapping("/api/v1/social")
class SocialInteractionController(private val service: SocialInteractionService) {
    @PostMapping("/follows/{nearbyHandle}")
    fun follow(principal: Principal, @PathVariable nearbyHandle: String): RelationshipResponse =
        service.follow(UUID.fromString(principal.name), nearbyHandle)

    @DeleteMapping("/follows/{nearbyHandle}")
    fun unfollow(principal: Principal, @PathVariable nearbyHandle: String): RelationshipResponse =
        service.unfollow(UUID.fromString(principal.name), nearbyHandle)

    @PostMapping("/blocks/{nearbyHandle}")
    fun block(principal: Principal, @PathVariable nearbyHandle: String): RelationshipResponse =
        service.block(UUID.fromString(principal.name), nearbyHandle)

    @DeleteMapping("/blocks/{nearbyHandle}")
    fun unblock(principal: Principal, @PathVariable nearbyHandle: String): RelationshipResponse =
        service.unblock(UUID.fromString(principal.name), nearbyHandle)

    @PostMapping("/reports/{nearbyHandle}")
    fun report(
        principal: Principal,
        @PathVariable nearbyHandle: String,
        @RequestBody request: ReportUserRequest,
    ): ReportUserResponse = service.report(UUID.fromString(principal.name), nearbyHandle, request)
}
