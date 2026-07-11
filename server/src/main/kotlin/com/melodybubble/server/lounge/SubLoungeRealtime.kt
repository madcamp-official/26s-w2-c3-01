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
        val stale = jdbc.query(
            """
            select member.user_id,member.sub_lounge_id from sub_lounge_members member
            join sub_lounges room on room.id=member.sub_lounge_id
            left join building_lounge_sessions session
              on session.building_lounge_id=room.building_lounge_id and session.user_id=member.user_id
            where member.active=true
              and (session.id is null or session.active=false or session.expires_at<=now())
            limit 200
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) to UUID.fromString(rs.getString(2)) },
        )
        stale.forEach { (userId, subLoungeId) ->
            deactivateMembership(userId, subLoungeId)
            publishMemberChange(subLoungeId, userId, joined = false)
            publishState(subLoungeId)
        }
    }

    @Transactional
    fun updateListening(userId: UUID, subLoungeId: UUID, request: UpdateLoungeListeningRequest) {
        requireMember(userId, subLoungeId)
        rateLimiter.enforce(userId, "LOUNGE_LISTENING", 40, Duration.ofMinutes(1))
        val title = request.trackTitle.trim().take(160)
        val artist = request.artistName.trim().take(160)
        if (!request.isPlaying) {
            jdbc.update(
                "delete from sub_lounge_listening_statuses where sub_lounge_id=? and user_id=?",
                subLoungeId,
                userId,
            )
        } else {
            if (title.isBlank() || artist.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "재생 중인 곡 정보가 필요합니다.")
            }
            jdbc.update(
                """
                insert into sub_lounge_listening_statuses(
                  sub_lounge_id,user_id,track_title,artist_name,album_art_url,is_playing,expires_at
                ) values (?,?,?,?,?,true,now()+interval '90 seconds')
                on conflict(sub_lounge_id,user_id) do update set
                  track_title=excluded.track_title,artist_name=excluded.artist_name,
                  album_art_url=excluded.album_art_url,is_playing=true,
                  updated_at=now(),expires_at=excluded.expires_at
                """.trimIndent(),
                subLoungeId,
                userId,
                title,
                artist,
                request.albumArtUrl?.take(2_000),
            )
        }
        realtime.toTopicAfterCommit(
            topic(subLoungeId),
            RealtimeEventTypes.LISTENING_STATUS_UPDATED,
            listeningPayload(userId, title, artist, request),
        )
    }

    @Transactional
    fun addCard(userId: UUID, subLoungeId: UUID, request: CreateLoungeCardRequest): LoungeRecommendationCard {
        requireMember(userId, subLoungeId)
        rateLimiter.enforce(userId, "LOUNGE_CARD", 10, Duration.ofMinutes(1))
        val clientCardId = runCatching { UUID.fromString(request.clientCardId) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 clientCardId가 필요합니다.")
        }
        val title = request.trackTitle.trim().take(160)
        val artist = request.artistName.trim().take(160)
        if (title.isBlank() || artist.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "곡 제목과 아티스트가 필요합니다.")
        }
        val inserted = jdbc.update(
            """
            insert into sub_lounge_recommendation_cards(
              id,sub_lounge_id,sender_id,client_card_id,track_title,artist_name,message
            ) values (gen_random_uuid(),?,?,?,?,?,?)
            on conflict(sender_id,client_card_id) do nothing
            """.trimIndent(),
            subLoungeId,
            userId,
            clientCardId,
            title,
            artist,
            request.message?.trim()?.take(240)?.ifBlank { null },
        )
        val card = cardByClientId(userId, clientCardId)
        if (card.subLoungeId != subLoungeId || card.trackTitle != title || card.artistName != artist ||
            card.message != request.message?.trim()?.take(240)?.ifBlank { null }
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "clientCardId가 다른 카드에 사용되었습니다.")
        }
        if (inserted == 1) {
            realtime.toTopicAfterCommit(
                topic(subLoungeId),
                RealtimeEventTypes.RECOMMENDATION_CARD_CREATED,
                card,
            )
        }
        return card
    }

    @Transactional
    fun react(userId: UUID, cardId: UUID, request: ReactionRequest): LoungeRecommendationCard {
        val subLoungeId = cardRoom(cardId)
        requireMember(userId, subLoungeId)
        rateLimiter.enforce(userId, "LOUNGE_REACTION", 30, Duration.ofMinutes(1))
        val type = request.reactionType.trim().uppercase()
        if (type !in REACTION_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 리액션입니다.")
        }
        jdbc.update(
            """
            insert into sub_lounge_card_reactions(card_id,user_id,reaction_type)
            values (?,?,?) on conflict(card_id,user_id,reaction_type) do nothing
            """.trimIndent(),
            cardId,
            userId,
            type,
        )
        val card = cards(userId, subLoungeId).first { it.id == cardId }
        realtime.toTopicAfterCommit(
            topic(subLoungeId),
            RealtimeEventTypes.RECOMMENDATION_CARD_REACTED,
            card,
        )
        return card
    }

    @Transactional
    fun vote(userId: UUID, subLoungeId: UUID, request: LoungeVoteRequest): LoungePollState {
        requireMember(userId, subLoungeId)
        rateLimiter.enforce(userId, "LOUNGE_VOTE", 20, Duration.ofMinutes(1))
        val key = request.targetKey.trim().uppercase()
        if (key !in POLL_KEYS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 투표 항목입니다.")
        jdbc.update(
            """
            insert into sub_lounge_votes(sub_lounge_id,user_id,target_key) values (?,?,?)
            on conflict(sub_lounge_id,user_id) do update set target_key=excluded.target_key,updated_at=now()
            """.trimIndent(),
            subLoungeId,
            userId,
            key,
        )
        val poll = poll(userId, subLoungeId)
        realtime.toTopicAfterCommit(topic(subLoungeId), RealtimeEventTypes.LOUNGE_POLL_UPDATED, poll)
        return poll
    }

    fun cards(userId: UUID, subLoungeId: UUID): List<LoungeRecommendationCard> {
        requireMember(userId, subLoungeId)
        return jdbc.query(
            """
            select card.id,card.client_card_id,sender.display_name,card.track_title,card.artist_name,
              card.message,card.created_at,count(reaction.user_id) reaction_count,
              bool_or(reaction.user_id=?) reacted_by_me
            from sub_lounge_recommendation_cards card
            join users sender on sender.id=card.sender_id
            left join sub_lounge_card_reactions reaction on reaction.card_id=card.id
            where card.sub_lounge_id=?
            group by card.id,sender.display_name
            order by card.created_at desc limit 50
            """.trimIndent(),
            { rs, _ ->
                LoungeRecommendationCard(
                    id = UUID.fromString(rs.getString("id")),
                    subLoungeId = subLoungeId,
                    clientCardId = UUID.fromString(rs.getString("client_card_id")),
                    senderAlias = rs.getString("display_name"),
                    trackTitle = rs.getString("track_title"),
                    artistName = rs.getString("artist_name"),
                    message = rs.getString("message"),
                    reactionCount = rs.getInt("reaction_count"),
                    reactedByMe = rs.getBoolean("reacted_by_me"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
            userId,
            subLoungeId,
        )
    }

    private fun snapshotUnchecked(userId: UUID, subLoungeId: UUID): SubLoungeSnapshot {
        val room = jdbc.query(
            """
            select room.id,room.building_lounge_id,room.title,room.style
            from sub_lounges room where room.id=? and room.active=true
            """.trimIndent(),
            { rs, _ -> arrayOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)) },
            subLoungeId,
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "하위 라운지를 찾을 수 없습니다.")
        return SubLoungeSnapshot(
            id = UUID.fromString(room[0]),
            buildingLoungeId = UUID.fromString(room[1]),
            title = room[2],
            style = room[3],
            memberCount = memberCount(subLoungeId),
            joined = hasActiveMembership(jdbc, userId, subLoungeId),
            listeningStatuses = listeningStatuses(subLoungeId),
            cards = cards(userId, subLoungeId),
            poll = poll(userId, subLoungeId),
        )
    }

    private fun listeningStatuses(subLoungeId: UUID): List<LoungeListeningStatus> = jdbc.query(
        """
        select person.display_name,status.track_title,status.artist_name,status.album_art_url,
          status.is_playing,status.updated_at
        from sub_lounge_listening_statuses status
        join users person on person.id=status.user_id
        join sub_lounge_members member on member.sub_lounge_id=status.sub_lounge_id
          and member.user_id=status.user_id and member.active=true
        where status.sub_lounge_id=? and status.expires_at>now()
        order by status.updated_at desc
        """.trimIndent(),
        { rs, _ ->
            LoungeListeningStatus(
                listenerAlias = rs.getString("display_name"),
                trackTitle = rs.getString("track_title"),
                artistName = rs.getString("artist_name"),
                albumArtUrl = rs.getString("album_art_url"),
                isPlaying = rs.getBoolean("is_playing"),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
            )
        },
        subLoungeId,
    )

    private fun poll(userId: UUID, subLoungeId: UUID): LoungePollState {
        val counts = jdbc.query(
            "select target_key,count(*) from sub_lounge_votes where sub_lounge_id=? group by target_key",
            { rs, _ -> rs.getString(1) to rs.getInt(2) },
            subLoungeId,
        ).toMap()
        val myVote = jdbc.query(
            "select target_key from sub_lounge_votes where sub_lounge_id=? and user_id=?",
            { rs, _ -> rs.getString(1) },
            subLoungeId,
            userId,
        ).firstOrNull()
        return LoungePollState(POLL_KEYS.map { LoungePollOption(it, counts[it] ?: 0) }, myVote)
    }

    private fun requireBuildingSession(userId: UUID, subLoungeId: UUID): UUID = jdbc.query(
        """
        select room.building_lounge_id from sub_lounges room
        join building_lounge_sessions session on session.building_lounge_id=room.building_lounge_id
        where room.id=? and room.active=true and session.user_id=?
          and session.active=true and session.expires_at>now()
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString(1)) },
        subLoungeId,
        userId,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "건물 라운지에 먼저 입장해 주세요.")

    private fun requireMember(userId: UUID, subLoungeId: UUID) {
        if (!hasActiveMembership(jdbc, userId, subLoungeId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "하위 라운지 참가가 필요합니다.")
        }
    }

    private fun deactivateMembership(userId: UUID, subLoungeId: UUID) {
        jdbc.update(
            "update sub_lounge_members set active=false,last_seen_at=now() where user_id=? and sub_lounge_id=?",
            userId,
            subLoungeId,
        )
        jdbc.update(
            "delete from sub_lounge_listening_statuses where user_id=? and sub_lounge_id=?",
            userId,
            subLoungeId,
        )
    }

    private fun memberCount(subLoungeId: UUID): Int = activeMemberCount(jdbc, subLoungeId)

    private fun publishState(subLoungeId: UUID) {
