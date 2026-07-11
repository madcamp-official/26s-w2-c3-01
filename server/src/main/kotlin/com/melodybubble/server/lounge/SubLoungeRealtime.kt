package com.melodybubble.server.lounge

import com.melodybubble.server.realtime.RealtimeEventTypes
import com.melodybubble.server.realtime.RealtimePublisher
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class LoungeListeningStatus(
    val listenerAlias: String,
    val trackTitle: String?,
    val artistName: String?,
    val albumArtUrl: String?,
    val isPlaying: Boolean,
    val updatedAt: Instant,
)

data class LoungeRecommendationCard(
    val id: UUID,
    val subLoungeId: UUID,
    val clientCardId: UUID,
    val senderAlias: String,
    val trackTitle: String,
    val artistName: String,
    val message: String?,
    val reactionCount: Int,
    val reactedByMe: Boolean,
    val createdAt: Instant,
)

data class LoungePollOption(val key: String, val voteCount: Int)
data class LoungePollState(val options: List<LoungePollOption>, val myVote: String?)

data class SubLoungeSnapshot(
    val id: UUID,
    val buildingLoungeId: UUID,
    val title: String,
    val style: String?,
    val memberCount: Int,
    val joined: Boolean,
    val listeningStatuses: List<LoungeListeningStatus>,
    val cards: List<LoungeRecommendationCard>,
    val poll: LoungePollState,
    val generatedAt: Instant = Instant.now(),
)

data class UpdateLoungeListeningRequest(
    val trackTitle: String = "",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = true,
)

data class CreateLoungeCardRequest(
    val clientCardId: String,
    val trackTitle: String,
    val artistName: String,
    val message: String? = null,
)

data class LoungeVoteRequest(val targetKey: String)
data class SubLoungeMemberPayload(val memberCount: Int, val memberAlias: String, val updatedAt: Instant = Instant.now())
data class SubLoungeStatePayload(val memberCount: Int, val updatedAt: Instant = Instant.now())

@Component
class SubLoungeTopicAuthorizer(private val jdbc: JdbcTemplate) {
    fun canSubscribe(userId: UUID, destination: String): Boolean {
        val match = TOPIC_PATTERN.matchEntire(destination) ?: return false
        val subLoungeId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull() ?: return false
        return hasActiveMembership(jdbc, userId, subLoungeId)
    }

    companion object {
        private val TOPIC_PATTERN = Regex("/topic/sub-lounges/([0-9a-fA-F-]{36})")
    }
}

@Service
class SubLoungeRealtimeService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val realtime: RealtimePublisher,
) {
    fun snapshot(userId: UUID, subLoungeId: UUID): SubLoungeSnapshot {
        requireMember(userId, subLoungeId)
        return snapshotUnchecked(userId, subLoungeId)
    }

    fun activeSnapshot(userId: UUID): SubLoungeSnapshot? {
        val subLoungeId = jdbc.query(
            """
            select member.sub_lounge_id from sub_lounge_members member
            join sub_lounges room on room.id=member.sub_lounge_id and room.active=true
            join building_lounge_sessions session
              on session.building_lounge_id=room.building_lounge_id and session.user_id=member.user_id
            where member.user_id=? and member.active=true
              and session.active=true and session.expires_at>now()
            order by member.last_seen_at desc limit 1
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            userId,
        ).firstOrNull() ?: return null
        return snapshotUnchecked(userId, subLoungeId)
    }

    @Transactional
    fun join(userId: UUID, subLoungeId: UUID): SubLoungeSnapshot {
        rateLimiter.enforce(userId, "LOUNGE_JOIN", 30, Duration.ofMinutes(1))
        val buildingLoungeId = requireBuildingSession(userId, subLoungeId)
        val previousRooms = jdbc.query(
            """
            select member.sub_lounge_id from sub_lounge_members member
            join sub_lounges room on room.id=member.sub_lounge_id
            where member.user_id=? and member.active=true
              and room.building_lounge_id=? and member.sub_lounge_id<>?
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            userId,
            buildingLoungeId,
            subLoungeId,
        )
        previousRooms.forEach { previousId -> deactivateMembership(userId, previousId) }
        val wasJoined = hasActiveMembership(jdbc, userId, subLoungeId)
        jdbc.update(
            """
            insert into sub_lounge_members(sub_lounge_id,user_id,active)
            values (?,?,true)
            on conflict(sub_lounge_id,user_id) do update
              set active=true,last_seen_at=now()
            """.trimIndent(),
            subLoungeId,
            userId,
        )
        previousRooms.forEach { publishMemberChange(it, userId, joined = false) }
        if (!wasJoined) publishMemberChange(subLoungeId, userId, joined = true)
        publishState(subLoungeId)
        return snapshotUnchecked(userId, subLoungeId)
    }

    @Transactional
    fun leave(userId: UUID, subLoungeId: UUID) {
        if (!hasActiveMembership(jdbc, userId, subLoungeId)) return
        deactivateMembership(userId, subLoungeId)
        publishMemberChange(subLoungeId, userId, joined = false)
        publishState(subLoungeId)
    }

    @Transactional
    fun forceLeaveFromBuilding(userId: UUID, buildingLoungeId: UUID) {
        val rooms = jdbc.query(
            """
            select member.sub_lounge_id from sub_lounge_members member
            join sub_lounges room on room.id=member.sub_lounge_id
            where member.user_id=? and member.active=true and room.building_lounge_id=?
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            userId,
            buildingLoungeId,
        )
        rooms.forEach { subLoungeId ->
            deactivateMembership(userId, subLoungeId)
            publishMemberChange(subLoungeId, userId, joined = false)
            publishState(subLoungeId)
        }
    }

    @Transactional
    fun expireStaleMemberships() {
