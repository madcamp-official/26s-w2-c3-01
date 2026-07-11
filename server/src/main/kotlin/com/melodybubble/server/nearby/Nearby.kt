package com.melodybubble.server.nearby

import com.melodybubble.server.realtime.RealtimeEnvelope
import com.melodybubble.server.realtime.RealtimeEventTypes
import com.melodybubble.server.realtime.RealtimePublisher
import com.melodybubble.server.realtime.RealtimeQueues
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TrackSummary(val title: String, val artist: String, val albumArtUrl: String? = null)
data class AbstractPosition(val x: Float, val y: Float)
data class NearbyBubble(
    val nearbyHandle: String,
    val displayAlias: String,
    val profileColor: String,
    val displayPosition: AbstractPosition,
    val matchScore: Int,
    val proximity: String,
    val relationship: String,
    val canReact: Boolean,
    val track: TrackSummary?,
)
data class NearbySnapshot(
    val generatedAt: Instant = Instant.now(),
    val radiusMeters: Int,
    val items: List<NearbyBubble>,
)
data class LocationUpdate(
    val requestId: String,
    val clientSessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)
data class MusicUpdate(
    val title: String = "",
    val artist: String = "",
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val isPlaying: Boolean = true,
)
data class PopularTrack(
    val title: String,
    val artist: String,
    val listenerCount: Int,
    val reactionCount: Int,
)
data class NearbyMusicUpdatedPayload(
    val nearbyHandle: String,
    val isPlaying: Boolean,
    val track: TrackSummary?,
)
data class PopularTracksUpdatedPayload(val tracks: List<PopularTrack>)

data class MusicAudienceMember(val userId: UUID, val sourceHandle: String)
private data class CurrentMusic(val title: String, val artist: String)

@Service
class NearbyService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val realtime: RealtimePublisher,
    @Value("\${app.nearby.presence-ttl-seconds:90}") private val presenceTtlSeconds: Long,
    @Value("\${app.nearby.max-radius-meters:2000}") private val maxRadiusMeters: Int,
) {
    fun snapshot(userId: UUID): NearbySnapshot {
        rateLimiter.enforce(userId, "NEARBY_SNAPSHOT", 60, Duration.ofMinutes(1))
        val radius = discoveryRadius(userId)
        val sql = """
          WITH my_location AS (
            SELECT location.point
            FROM current_locations location
            JOIN presence_sessions session ON session.id=location.session_id
            WHERE session.user_id=? AND session.expires_at>now() AND location.expires_at>now()
            ORDER BY location.updated_at DESC
            LIMIT 1
          ), candidates AS (
            SELECT DISTINCT ON (session.user_id)
              session.user_id,
              session.nearby_handle,
              person.display_name,
              person.profile_color,
              CASE WHEN status.is_playing AND privacy.share_music AND (
                privacy.music_visibility='TITLE_ARTIST' OR (
                  privacy.music_visibility='MUTUALS'
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?)
                )
              ) THEN status.track_title END AS track_title,
              CASE WHEN status.is_playing AND privacy.share_music AND (
                privacy.music_visibility='TITLE_ARTIST' OR (
                  privacy.music_visibility='MUTUALS'
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?)
                )
              ) THEN status.artist_name END AS artist_name,
              CASE
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                 AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?) THEN 'MUTUAL'
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id) THEN 'FOLLOWING'
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?) THEN 'FOLLOWS_ME'
                ELSE 'NONE'
              END AS relationship,
              privacy.allow_reactions,
              ST_DistanceSphere(mine.point, location.point) AS distance_meters
            FROM my_location mine
            JOIN current_locations location
              ON ST_DWithin(mine.point::geography, location.point::geography, ?)
             AND location.expires_at>now()
            JOIN presence_sessions session
              ON session.id=location.session_id AND session.expires_at>now() AND session.user_id<>?
            JOIN users person ON person.id=session.user_id
            JOIN user_privacy_settings privacy ON privacy.user_id=person.id
            LEFT JOIN music_statuses status ON status.user_id=person.id AND status.expires_at>now()
            WHERE privacy.discoverable=true
              AND privacy.discoverability_scope<>'HIDDEN'
              AND (
                privacy.discoverability_scope='NEARBY' OR (
                  privacy.discoverability_scope='MUTUALS'
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?)
                )
              )
              AND NOT EXISTS(
                SELECT 1 FROM user_blocks block
                WHERE (block.blocker_id=? AND block.blocked_id=session.user_id)
                   OR (block.blocker_id=session.user_id AND block.blocked_id=?)
              )
            ORDER BY session.user_id, distance_meters, session.last_seen_at DESC
          )
          SELECT * FROM candidates ORDER BY distance_meters LIMIT 40
        """.trimIndent()
        val repeatedUserIds = Array(12) { userId }
        val args = buildList<Any> {
            add(userId)
            addAll(repeatedUserIds.take(8))
            add(radius)
            add(userId)
            addAll(repeatedUserIds.takeLast(4))
        }.toTypedArray()
        val items = jdbc.query(sql, { rs, _ ->
            val handle = rs.getString("nearby_handle")
            NearbyBubble(
                nearbyHandle = handle,
                displayAlias = rs.getString("display_name"),
                profileColor = rs.getString("profile_color"),
                displayPosition = position(handle),
                matchScore = 65 + stable(handle, 31),
                proximity = proximity(rs.getDouble("distance_meters")),
                relationship = rs.getString("relationship"),
                canReact = rs.getBoolean("allow_reactions"),
                track = rs.getString("track_title")?.let {
                    TrackSummary(it, rs.getString("artist_name"))
                },
            )
        }, *args)
        return NearbySnapshot(radiusMeters = radius, items = items)
    }

    @Transactional
    fun updateLocation(userId: UUID, update: LocationUpdate) {
        rateLimiter.enforce(userId, "PRESENCE_LOCATION", 30, Duration.ofMinutes(1))
        validateLocation(update)
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(presenceTtlSeconds.coerceIn(30, 300)))
        val session = jdbc.query(
            """
            INSERT INTO presence_sessions(id,user_id,client_session_id,nearby_handle,expires_at)
            VALUES (gen_random_uuid(), ?, ?, concat('n_', replace(gen_random_uuid()::text,'-','')), ?)
            ON CONFLICT(user_id,client_session_id) DO UPDATE
              SET expires_at=excluded.expires_at,last_seen_at=now()
            RETURNING id
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            userId,
            update.clientSessionId,
            expiresAt,
        ).single()
        jdbc.update(
            """
            INSERT INTO current_locations(session_id,point,accuracy_meters,expires_at)
            VALUES (?, ST_SetSRID(ST_MakePoint(?,?),4326), ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              point=excluded.point,accuracy_meters=excluded.accuracy_meters,
              expires_at=excluded.expires_at,updated_at=now()
            """.trimIndent(),
            session,
            update.longitude,
            update.latitude,
            update.accuracyMeters,
            expiresAt,
        )
    }

    @Transactional
    fun updateMusic(userId: UUID, update: MusicUpdate) {
        rateLimiter.enforce(userId, "PRESENCE_MUSIC", 30, Duration.ofMinutes(1))
        val previous = currentMusic(userId)
        if (!update.isPlaying) {
            jdbc.update("delete from music_statuses where user_id=?", userId)
            if (previous != null) publishMusicChanged(userId, false, null)
            return
        }
        val title = update.title.trim()
        val artist = update.artist.trim()
        if (title.isBlank() || artist.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "재생 중인 음악의 제목과 아티스트가 필요합니다.")
        }
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(presenceTtlSeconds.coerceIn(30, 300)))
        val normalizedTitle = title.take(160)
        val normalizedArtist = artist.take(160)
        val sourceType = update.sourceType.trim().take(32).ifBlank { "ANDROID_MEDIA_SESSION" }
        jdbc.update(
            """
            INSERT INTO music_statuses(id,user_id,track_title,artist_name,source_type,is_playing,expires_at)
            VALUES (gen_random_uuid(),?,?,?,?,true,?)
            ON CONFLICT(user_id) DO UPDATE SET
              track_title=excluded.track_title,artist_name=excluded.artist_name,
              source_type=excluded.source_type,is_playing=true,
              expires_at=excluded.expires_at,updated_at=now()
            """.trimIndent(),
            userId,
            normalizedTitle,
            normalizedArtist,
            sourceType,
            expiresAt,
        )
        val changed = previous == null || previous.title != normalizedTitle || previous.artist != normalizedArtist
        if (changed) publishMusicChanged(userId, true, TrackSummary(normalizedTitle, normalizedArtist))
    }

    fun popularTracks(userId: UUID): List<PopularTrack> {
        rateLimiter.enforce(userId, "NEARBY_POPULAR", 60, Duration.ofMinutes(1))
        return popularTracksFor(userId)
    }

    /** Recomputes popular-track payloads for users who can currently see this listener. */
    fun publishPopularTracksAroundAfterCommit(listenerId: UUID) {
        musicAudience(listenerId).forEach { audience ->
            realtime.toUserAfterCommit(
                audience.userId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.POPULAR_TRACKS_UPDATED,
                PopularTracksUpdatedPayload(popularTracksFor(audience.userId)),
            )
        }
    }

    fun musicAudienceSnapshot(sourceUserId: UUID): List<MusicAudienceMember> =
        musicAudience(sourceUserId)

    fun publishPrivacyAudienceChangesAfterCommit(
        sourceUserId: UUID,
        previousAudience: List<MusicAudienceMember>,
    ) {
        val remainingUserIds = musicAudience(sourceUserId).map(MusicAudienceMember::userId).toSet()
        previousAudience.filter { it.userId !in remainingUserIds }.forEach { removed ->
            realtime.toUserAfterCommit(
                removed.userId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.NEARBY_MUSIC_UPDATED,
                NearbyMusicUpdatedPayload(
                    nearbyHandle = removed.sourceHandle,
                    isPlaying = false,
                    track = null,
                ),
            )
            realtime.toUserAfterCommit(
                removed.userId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.POPULAR_TRACKS_UPDATED,
                PopularTracksUpdatedPayload(popularTracksFor(removed.userId)),
            )
        }
    }

    fun stop(userId: UUID, clientSessionId: String) {
        jdbc.update("delete from presence_sessions where user_id=? and client_session_id=?", userId, clientSessionId.take(128))
    }

    private fun currentMusic(userId: UUID): CurrentMusic? = jdbc.query(
        """
        select track_title,artist_name from music_statuses
        where user_id=? and is_playing=true and expires_at>now()
        """.trimIndent(),
        { rs, _ -> CurrentMusic(rs.getString(1), rs.getString(2)) },
        userId,
    ).firstOrNull()

    private fun publishMusicChanged(userId: UUID, isPlaying: Boolean, track: TrackSummary?) {
        musicAudience(userId).forEach { audience ->
            realtime.toUserAfterCommit(
                audience.userId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.NEARBY_MUSIC_UPDATED,
                NearbyMusicUpdatedPayload(
                    nearbyHandle = audience.sourceHandle,
                    isPlaying = isPlaying,
                    track = track,
                ),
            )
            realtime.toUserAfterCommit(
                audience.userId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.POPULAR_TRACKS_UPDATED,
                PopularTracksUpdatedPayload(popularTracksFor(audience.userId)),
            )
        }
    }

    private fun musicAudience(sourceUserId: UUID): List<MusicAudienceMember> = jdbc.query(
        """
        with source_locations as (
          select session.nearby_handle,location.point,session.last_seen_at
          from presence_sessions session
          join current_locations location on location.session_id=session.id
          where session.user_id=? and session.expires_at>now() and location.expires_at>now()
        )
        select distinct on (recipient_session.user_id)
          recipient_session.user_id recipient_id,source.nearby_handle
        from source_locations source
        join current_locations recipient_location
          on recipient_location.expires_at>now()
        join presence_sessions recipient_session
          on recipient_session.id=recipient_location.session_id
         and recipient_session.expires_at>now()
         and recipient_session.user_id<>?
        join user_privacy_settings recipient_privacy
          on recipient_privacy.user_id=recipient_session.user_id
        join user_privacy_settings source_privacy on source_privacy.user_id=?
        where ST_DWithin(
            source.point::geography,
            recipient_location.point::geography,
            recipient_privacy.discovery_radius_meters
          )
          and source_privacy.discoverable=true
          and source_privacy.share_music=true
          and source_privacy.discoverability_scope<>'HIDDEN'
          and source_privacy.music_visibility<>'HIDDEN'
          and (
            source_privacy.discoverability_scope='NEARBY' or (
              source_privacy.discoverability_scope='MUTUALS'
              and exists(select 1 from user_follows f where f.follower_id=recipient_session.user_id and f.followed_id=?)
              and exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=recipient_session.user_id)
            )
          )
          and (
            source_privacy.music_visibility='TITLE_ARTIST' or (
              source_privacy.music_visibility='MUTUALS'
              and exists(select 1 from user_follows f where f.follower_id=recipient_session.user_id and f.followed_id=?)
              and exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=recipient_session.user_id)
            )
          )
          and not exists(
            select 1 from user_blocks block
            where (block.blocker_id=? and block.blocked_id=recipient_session.user_id)
               or (block.blocker_id=recipient_session.user_id and block.blocked_id=?)
          )
        order by recipient_session.user_id,
          ST_DistanceSphere(source.point,recipient_location.point),source.last_seen_at desc
        """.trimIndent(),
        { rs, _ ->
            MusicAudienceMember(
                userId = UUID.fromString(rs.getString("recipient_id")),
                sourceHandle = rs.getString("nearby_handle"),
            )
        },
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
    )

    private fun popularTracksFor(userId: UUID): List<PopularTrack> {
        val radius = discoveryRadius(userId)
        return jdbc.query(
            """
            with my_location as (
              select location.point
              from current_locations location
              join presence_sessions session on session.id=location.session_id
              where session.user_id=? and session.expires_at>now() and location.expires_at>now()
              order by location.updated_at desc limit 1
            ), candidate_users as (
              select distinct session.user_id,
                privacy.discoverability_scope,privacy.music_visibility,privacy.share_music,
                (
                  exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=session.user_id)
                  and exists(select 1 from user_follows f where f.follower_id=session.user_id and f.followed_id=?)
                ) is_mutual
              from my_location mine
              join current_locations location
                on location.expires_at>now()
               and ST_DWithin(mine.point::geography,location.point::geography,?)
              join presence_sessions session
                on session.id=location.session_id and session.expires_at>now() and session.user_id<>?
              join user_privacy_settings privacy on privacy.user_id=session.user_id
              where privacy.discoverable=true
                and privacy.discoverability_scope<>'HIDDEN'
                and not exists(
                  select 1 from user_blocks block
                  where (block.blocker_id=? and block.blocked_id=session.user_id)
                     or (block.blocker_id=session.user_id and block.blocked_id=?)
                )
            ), visible_tracks as (
              select candidate.user_id,status.track_title,status.artist_name
              from candidate_users candidate
              join music_statuses status
                on status.user_id=candidate.user_id and status.is_playing=true and status.expires_at>now()
              where (
                  candidate.discoverability_scope='NEARBY'
                  or (candidate.discoverability_scope='MUTUALS' and candidate.is_mutual)
                )
                and candidate.share_music=true
                and (
                  candidate.music_visibility='TITLE_ARTIST'
                  or (candidate.music_visibility='MUTUALS' and candidate.is_mutual)
                )
            )
            select visible.track_title,visible.artist_name,
              count(distinct visible.user_id) listener_count,
              (
                select count(*) from nearby_reactions reaction
                join visible_tracks reacted_listener on reacted_listener.user_id=reaction.recipient_id
                where reaction.track_title=visible.track_title
                  and reaction.track_artist=visible.artist_name
                  and reacted_listener.track_title=visible.track_title
                  and reacted_listener.artist_name=visible.artist_name
              ) reaction_count
            from visible_tracks visible
            group by visible.track_title,visible.artist_name
            order by listener_count desc,reaction_count desc,visible.track_title,visible.artist_name
            limit 20
            """.trimIndent(),
            { rs, _ ->
                PopularTrack(
                    title = rs.getString("track_title"),
                    artist = rs.getString("artist_name"),
                    listenerCount = rs.getInt("listener_count"),
                    reactionCount = rs.getInt("reaction_count"),
                )
            },
            userId,
            userId,
            userId,
            radius,
            userId,
            userId,
            userId,
        )
    }

    private fun discoveryRadius(userId: UUID): Int = (jdbc.queryForObject(
        "select coalesce(discovery_radius_meters,300) from user_privacy_settings where user_id=?",
        Int::class.java,
        userId,
    ) ?: 300).coerceIn(50, maxRadiusMeters.coerceAtLeast(50))

    private fun validateLocation(update: LocationUpdate) {
        if (update.requestId.length !in 1..128 || update.clientSessionId.length !in 8..128) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 Presence 요청 식별자가 필요합니다.")
        }
        if (!update.latitude.isFinite() || update.latitude !in -90.0..90.0 ||
            !update.longitude.isFinite() || update.longitude !in -180.0..180.0
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 위치가 필요합니다.")
        }
        if (update.accuracyMeters != null &&
            (!update.accuracyMeters.isFinite() || update.accuracyMeters !in 0f..5000f)
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 위치 정확도가 필요합니다.")
        }
    }

    private fun proximity(distance: Double) = when {
        distance < 45 -> "VERY_CLOSE"
        distance < 130 -> "CLOSE"
        else -> "AROUND"
    }
    private fun stable(value: String, modulo: Int) = (value.hashCode().toUInt().toLong() % modulo).toInt()
    private fun position(handle: String) = AbstractPosition(
        (stable(handle, 73) + 14) / 100f,
        (stable(handle.reversed(), 73) + 14) / 100f,
    )
}

@RestController
@RequestMapping("/api/v1/nearby")
class NearbyController(private val nearby: NearbyService) {
    @GetMapping("/snapshot")
    fun snapshot(principal: Principal) = nearby.snapshot(UUID.fromString(principal.name))

    @PostMapping("/location")
    fun location(principal: Principal, @RequestBody update: LocationUpdate): NearbySnapshot {
        val userId = UUID.fromString(principal.name)
        nearby.updateLocation(userId, update)
        return nearby.snapshot(userId)
    }

    @PostMapping("/music")
    fun music(principal: Principal, @RequestBody update: MusicUpdate) =
        nearby.updateMusic(UUID.fromString(principal.name), update)

    @GetMapping("/popular-tracks")
    fun popularTracks(principal: Principal) = nearby.popularTracks(UUID.fromString(principal.name))

    @DeleteMapping("/presence/{clientSessionId}")
    fun stop(principal: Principal, @PathVariable clientSessionId: String) =
        nearby.stop(UUID.fromString(principal.name), clientSessionId)

    @GetMapping("/{handle}")
    fun detail(principal: Principal, @PathVariable handle: String): NearbyBubble =
        nearby.snapshot(UUID.fromString(principal.name)).items.firstOrNull { it.nearbyHandle == handle }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "주변 사용자가 더 이상 활성 상태가 아닙니다.")
}

@org.springframework.messaging.handler.annotation.MessageMapping("location/update")
@org.springframework.stereotype.Controller
class PresenceMessageController(
    private val nearby: NearbyService,
    private val realtime: RealtimePublisher,
) {
    @org.springframework.messaging.simp.annotation.SendToUser("/queue/ack")
    fun updateLocation(update: LocationUpdate, principal: Principal): RealtimeEnvelope<Map<String, String>> {
        val userId = UUID.fromString(principal.name)
        nearby.updateLocation(userId, update)
        realtime.toUserAfterCommit(
            userId,
            RealtimeQueues.NEARBY,
            RealtimeEventTypes.NEARBY_SNAPSHOT,
            nearby.snapshot(userId),
        )
        return RealtimeEnvelope(
            type = RealtimeEventTypes.ACK,
            payload = mapOf("requestId" to update.requestId),
        )
    }
}
