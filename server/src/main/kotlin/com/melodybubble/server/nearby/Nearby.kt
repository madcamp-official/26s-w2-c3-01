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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val NEARBY_RADIUS_METERS = 15

internal enum class ProximityBand {
    WITHIN_5M,
    WITHIN_10M,
    WITHIN_15M,
}

internal enum class DistanceConfidence {
    HIGH,
    LOW,
    UNKNOWN,
}

internal fun proximityBand(distanceMeters: Double): ProximityBand? = when {
    distanceMeters < 0.0 || !distanceMeters.isFinite() -> null
    distanceMeters <= 5.0 -> ProximityBand.WITHIN_5M
    distanceMeters <= 10.0 -> ProximityBand.WITHIN_10M
    distanceMeters <= NEARBY_RADIUS_METERS.toDouble() -> ProximityBand.WITHIN_15M
    else -> null
}

internal fun combinedHorizontalAccuracyMeters(
    viewerAccuracyMeters: Double?,
    targetAccuracyMeters: Double?,
): Double? {
    if (viewerAccuracyMeters == null || targetAccuracyMeters == null) return null
    if (!viewerAccuracyMeters.isFinite() || !targetAccuracyMeters.isFinite()) return null
    if (viewerAccuracyMeters < 0.0 || targetAccuracyMeters < 0.0) return null
    return hypot(viewerAccuracyMeters, targetAccuracyMeters)
}

internal fun distanceConfidence(
    distanceMeters: Double,
    combinedAccuracyMeters: Double?,
): DistanceConfidence {
    val accuracy = combinedAccuracyMeters ?: return DistanceConfidence.UNKNOWN
    val band = proximityBand(distanceMeters) ?: return DistanceConfidence.LOW
    val lowerBand = proximityBand((distanceMeters - accuracy).coerceAtLeast(0.0))
    val upperBand = proximityBand(distanceMeters + accuracy)
    return if (lowerBand == band && upperBand == band) {
        DistanceConfidence.HIGH
    } else {
        DistanceConfidence.LOW
    }
}

/**
 * Produces a stable drawing coordinate whose angle is unrelated to physical bearing.
 * The radial annulus communicates only the 5 m, 10 m, or 15 m proximity band.
 */
internal fun abstractPosition(handle: String, band: ProximityBand): AbstractPosition {
    val angle = stableHash(handle, 360) * PI / 180.0
    val jitter = stableHash("radius:$handle", 1_000) / 1_000f
    val radius = when (band) {
        ProximityBand.WITHIN_5M -> 0.05f + jitter * 0.08f
        ProximityBand.WITHIN_10M -> 0.16f + jitter * 0.11f
        ProximityBand.WITHIN_15M -> 0.30f + jitter * 0.11f
    }
    return AbstractPosition(
        x = (0.5 + cos(angle) * radius).toFloat(),
        y = (0.5 + sin(angle) * radius).toFloat(),
    )
}

private fun stableHash(value: String, modulo: Int): Int =
    (value.hashCode().toUInt().toLong() % modulo).toInt()

data class TrackSummary(val title: String, val artist: String, val albumArtUrl: String? = null)
data class AbstractPosition(val x: Float, val y: Float)
data class NearbyBubble(
    val nearbyHandle: String,
    val profileHandle: String,
    val displayAlias: String,
    val avatarSeed: String?,
    val avatarUrl: String?,
    val profileColor: String,
    val displayPosition: AbstractPosition,
    val matchScore: Int,
    val proximity: String,
    val distanceConfidence: String = DistanceConfidence.UNKNOWN.name,
    val distanceAccuracyMeters: Double? = null,
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
    val sequence: Long = 0L,
    val observedAtEpochMillis: Long? = null,
    val source: String = "UNKNOWN",
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)
data class MusicUpdate(
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val artworkUrl: String? = null,
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val isPlaying: Boolean = true,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionObservedAt: Instant? = null,
    val observedAt: Instant? = null,
)
data class PopularTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
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
private data class CurrentMusic(val title: String, val artist: String, val artworkUrl: String?)

@Service
class NearbyService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val realtime: RealtimePublisher,
    @Value("\${app.nearby.presence-ttl-seconds:90}") private val presenceTtlSeconds: Long,
    @Value("\${app.nearby.max-radius-meters:15}") private val maxRadiusMeters: Int,
) {
    fun directBubble(viewerId: UUID, targetId: UUID): NearbyBubble? = jdbc.query(
        """
        select coalesce(ps.nearby_handle,concat('d_',replace(u.id::text,'-',''))) nearby_handle,
          u.profile_handle,u.display_name,u.avatar_seed,u.profile_color,
          (p.allow_reactions and ps.nearby_handle is not null) allow_reactions,
          case
            when exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=u.id)
             and exists(select 1 from user_follows f where f.follower_id=u.id and f.followed_id=?) then 'MUTUAL'
            when exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=u.id) then 'FOLLOWING'
            when exists(select 1 from user_follows f where f.follower_id=u.id and f.followed_id=?) then 'FOLLOWS_ME'
            else 'NONE' end relationship,
          case when m.is_playing and p.share_music and p.music_visibility='TITLE_ARTIST' then m.track_title end track_title,
          case when m.is_playing and p.share_music and p.music_visibility='TITLE_ARTIST' then m.artist_name end artist_name,
          case when m.is_playing and p.share_music and p.music_visibility='TITLE_ARTIST' then m.album_art_url end album_art_url
        from users u
        join user_privacy_settings p on p.user_id=u.id
        left join lateral (
          select nearby_handle from presence_sessions
          where user_id=u.id and expires_at>now()
          order by last_seen_at desc limit 1
        ) ps on true
        left join music_statuses m on m.user_id=u.id and m.expires_at>now()
        where u.id=? and u.id<>? and p.discoverable=true and p.discoverability_scope<>'HIDDEN'
          and (p.discoverability_scope='NEARBY' or (
            p.discoverability_scope='MUTUALS'
            and exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=u.id)
            and exists(select 1 from user_follows f where f.follower_id=u.id and f.followed_id=?)))
          and not exists(select 1 from user_blocks b where
            (b.blocker_id=? and b.blocked_id=u.id) or (b.blocker_id=u.id and b.blocked_id=?))
        limit 1
        """.trimIndent(),
        { rs, _ ->
            val handle = rs.getString("nearby_handle")
            NearbyBubble(
                nearbyHandle = handle,
                profileHandle = rs.getString("profile_handle"),
                displayAlias = rs.getString("display_name"),
                avatarSeed = rs.getString("avatar_seed"),
                avatarUrl = null,
                profileColor = rs.getString("profile_color"),
                displayPosition = abstractPosition(handle, ProximityBand.WITHIN_5M),
                matchScore = 65 + stable(handle, 31),
                proximity = ProximityBand.WITHIN_5M.name,
                relationship = rs.getString("relationship"),
                canReact = rs.getBoolean("allow_reactions"),
                track = rs.getString("track_title")?.let {
                    TrackSummary(it, rs.getString("artist_name"), rs.getString("album_art_url"))
                },
            )
        },
        viewerId, viewerId, viewerId, viewerId, targetId, viewerId,
        viewerId, viewerId, viewerId, viewerId,
    ).firstOrNull()

    fun snapshot(userId: UUID): NearbySnapshot = snapshot(userId, enforceRateLimit = true)

    private fun snapshot(userId: UUID, enforceRateLimit: Boolean): NearbySnapshot {
        if (enforceRateLimit) rateLimiter.enforce(userId, "NEARBY_SNAPSHOT", 60, Duration.ofMinutes(1))
        val radius = discoveryRadius(userId)
        val sql = """
          WITH my_location AS (
            SELECT location.point, location.accuracy_meters
            FROM current_locations location
            JOIN presence_sessions session ON session.id=location.session_id
            WHERE session.user_id=? AND session.expires_at>now() AND location.expires_at>now()
            ORDER BY location.updated_at DESC
            LIMIT 1
          ), candidates AS (
            SELECT DISTINCT ON (session.user_id)
              session.user_id,
              session.nearby_handle,
              person.profile_handle,
              person.display_name,
              person.avatar_seed,
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
              CASE WHEN status.is_playing AND privacy.share_music AND (
                privacy.music_visibility='TITLE_ARTIST' OR (
                  privacy.music_visibility='MUTUALS'
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                  AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?)
                )
              ) THEN status.album_art_url END AS album_art_url,
              CASE
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id)
                 AND EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?) THEN 'MUTUAL'
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=session.user_id) THEN 'FOLLOWING'
                WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=session.user_id AND f.followed_id=?) THEN 'FOLLOWS_ME'
                ELSE 'NONE'
              END AS relationship,
              privacy.allow_reactions,
              mine.accuracy_meters AS viewer_accuracy_meters,
              location.accuracy_meters AS target_accuracy_meters,
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
        val repeatedUserIds = Array(14) { userId }
        val args = buildList<Any> {
            add(userId)
            addAll(repeatedUserIds.take(10))
            add(radius)
            add(userId)
            addAll(repeatedUserIds.takeLast(4))
        }.toTypedArray()
        val items = jdbc.query(sql, { rs, _ ->
            val handle = rs.getString("nearby_handle")
            val distanceMeters = rs.getDouble("distance_meters").coerceAtMost(radius.toDouble())
            val viewerAccuracy = rs.getDouble("viewer_accuracy_meters").takeUnless { rs.wasNull() }
            val targetAccuracy = rs.getDouble("target_accuracy_meters").takeUnless { rs.wasNull() }
            val combinedAccuracy = combinedHorizontalAccuracyMeters(viewerAccuracy, targetAccuracy)
            val band = proximityBand(distanceMeters)
                ?: ProximityBand.WITHIN_15M
            NearbyBubble(
                nearbyHandle = handle,
                profileHandle = rs.getString("profile_handle"),
                displayAlias = rs.getString("display_name"),
                avatarSeed = rs.getString("avatar_seed"),
                avatarUrl = null,
                profileColor = rs.getString("profile_color"),
                displayPosition = abstractPosition(handle, band),
                matchScore = 65 + stable(handle, 31),
                proximity = band.name,
                distanceConfidence = distanceConfidence(distanceMeters, combinedAccuracy).name,
                distanceAccuracyMeters = combinedAccuracy,
                relationship = rs.getString("relationship"),
                canReact = rs.getBoolean("allow_reactions"),
                track = rs.getString("track_title")?.let {
                    TrackSummary(it, rs.getString("artist_name"), rs.getString("album_art_url"))
                },
            )
        }, *args)
        return NearbySnapshot(radiusMeters = radius, items = items)
    }

    @Transactional
    fun updateLocation(userId: UUID, update: LocationUpdate) {
        rateLimiter.enforce(userId, "PRESENCE_LOCATION", 300, Duration.ofMinutes(1))
        validateLocation(update)
        val receivedAt = Instant.now()
        val sequence = update.sequence.takeIf { it > 0L } ?: receivedAt.toEpochMilli() * 1_000L
        val observedAtMillis = update.observedAtEpochMillis?.takeIf {
            it in (receivedAt.toEpochMilli() - 60_000L)..(receivedAt.toEpochMilli() + 10_000L)
        } ?: receivedAt.toEpochMilli()
        val observedAt = Instant.ofEpochMilli(observedAtMillis)
        val source = update.source.uppercase().takeIf { it in setOf("FUSED", "GPS") } ?: "UNKNOWN"
        val previousViewers = nearbyViewerIds(userId)
        val expiresAt = Timestamp.from(receivedAt.plusSeconds(presenceTtlSeconds.coerceIn(30, 300)))
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
        val updated = jdbc.update(
            """
            INSERT INTO current_locations(session_id,point,accuracy_meters,sequence,observed_at,source,expires_at)
            VALUES (?, ST_SetSRID(ST_MakePoint(?,?),4326), ?, ?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              point=excluded.point,accuracy_meters=excluded.accuracy_meters,
              sequence=excluded.sequence,observed_at=excluded.observed_at,source=excluded.source,
              expires_at=excluded.expires_at,updated_at=now()
            WHERE current_locations.sequence < excluded.sequence
            """.trimIndent(),
            session,
            update.longitude,
            update.latitude,
            update.accuracyMeters,
            sequence,
            Timestamp.from(observedAt),
            source,
            expiresAt,
        )
        if (updated == 0) return
        publishNearbySnapshotsAfterCommit(previousViewers + nearbyViewerIds(userId))
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
        val normalizedAlbum = update.album?.trim()?.take(160)?.ifBlank { null }
        val normalizedArtwork = update.artworkUrl?.trim()?.take(2_000)?.ifBlank { null }?.also {
            if (!it.startsWith("https://")) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "앨범 이미지는 HTTPS 주소여야 합니다.")
            }
        }
        val durationMs = update.durationMs?.also {
            if (it !in 0..86_400_000) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "재생 시간이 올바르지 않습니다.")
        }
        val positionMs = update.positionMs?.also {
            if (it !in 0..86_400_000) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "재생 위치가 올바르지 않습니다.")
        }
        val observedAt = update.observedAt?.takeUnless { it.isAfter(Instant.now().plusSeconds(60)) } ?: Instant.now()
        val positionObservedAt = update.positionObservedAt ?: observedAt
        val sourceType = update.sourceType.trim().take(32).ifBlank { "ANDROID_MEDIA_SESSION" }
        jdbc.update(
            """
            INSERT INTO music_statuses(
              id,user_id,track_title,artist_name,album_name,album_art_url,source_type,is_playing,
              duration_ms,position_ms,position_observed_at,observed_at,expires_at
            )
            VALUES (gen_random_uuid(),?,?,?,?,?,?,true,?,?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET
              track_title=excluded.track_title,artist_name=excluded.artist_name,
              album_name=excluded.album_name,album_art_url=excluded.album_art_url,
              source_type=excluded.source_type,is_playing=true,duration_ms=excluded.duration_ms,
              position_ms=excluded.position_ms,position_observed_at=excluded.position_observed_at,
              observed_at=excluded.observed_at,
              expires_at=excluded.expires_at,updated_at=now()
            """.trimIndent(),
            userId,
            normalizedTitle,
            normalizedArtist,
            normalizedAlbum,
            normalizedArtwork,
            sourceType,
            durationMs,
            positionMs,
            Timestamp.from(positionObservedAt),
            Timestamp.from(observedAt),
            expiresAt,
        )
        val changed = previous == null || previous.title != normalizedTitle ||
            previous.artist != normalizedArtist || previous.artworkUrl != normalizedArtwork
        if (changed) {
            publishMusicChanged(
                userId,
                true,
                TrackSummary(normalizedTitle, normalizedArtist, normalizedArtwork),
            )
        }
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
        val previousViewers = nearbyViewerIds(userId)
        jdbc.update("delete from presence_sessions where user_id=? and client_session_id=?", userId, clientSessionId.take(128))
        publishNearbySnapshotsAfterCommit(previousViewers)
    }

    private fun publishNearbySnapshotsAfterCommit(viewerIds: Set<UUID>) {
        viewerIds.forEach { viewerId ->
            realtime.toUserAfterCommit(
                viewerId,
                RealtimeQueues.NEARBY,
                RealtimeEventTypes.NEARBY_SNAPSHOT,
                snapshot(viewerId, enforceRateLimit = false),
            )
        }
    }

    private fun nearbyViewerIds(sourceUserId: UUID): Set<UUID> = jdbc.query(
        """
        with source_locations as (
          select location.point
          from presence_sessions session
          join current_locations location on location.session_id=session.id
          where session.user_id=? and session.expires_at>now() and location.expires_at>now()
        )
        select distinct viewer_session.user_id
        from source_locations source
        join current_locations viewer_location
          on viewer_location.expires_at>now()
         and ST_DWithin(
           source.point::geography,
           viewer_location.point::geography,
           ?
         )
        join presence_sessions viewer_session
          on viewer_session.id=viewer_location.session_id
         and viewer_session.expires_at>now()
         and viewer_session.user_id<>?
        join user_privacy_settings source_privacy on source_privacy.user_id=?
        where source_privacy.discoverable=true
          and source_privacy.discoverability_scope<>'HIDDEN'
          and (
            source_privacy.discoverability_scope='NEARBY' or (
              source_privacy.discoverability_scope='MUTUALS'
              and exists(select 1 from user_follows f where f.follower_id=viewer_session.user_id and f.followed_id=?)
              and exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=viewer_session.user_id)
            )
          )
          and not exists(
            select 1 from user_blocks block
            where (block.blocker_id=? and block.blocked_id=viewer_session.user_id)
               or (block.blocker_id=viewer_session.user_id and block.blocked_id=?)
          )
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString(1)) },
        sourceUserId,
        NEARBY_RADIUS_METERS,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
        sourceUserId,
    ).toSet()

    private fun currentMusic(userId: UUID): CurrentMusic? = jdbc.query(
        """
        select track_title,artist_name,album_art_url from music_statuses
        where user_id=? and is_playing=true and expires_at>now()
        """.trimIndent(),
        { rs, _ -> CurrentMusic(rs.getString(1), rs.getString(2), rs.getString(3)) },
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
            least(recipient_privacy.discovery_radius_meters, 15)
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
              select candidate.user_id,status.track_title,status.artist_name,status.album_art_url
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
            select visible.track_title,visible.artist_name,max(visible.album_art_url) artwork_url,
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
                    artworkUrl = rs.getString("artwork_url"),
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
        "select coalesce(discovery_radius_meters,15) from user_privacy_settings where user_id=?",
        Int::class.java,
        userId,
    ) ?: NEARBY_RADIUS_METERS).coerceIn(1, minOf(NEARBY_RADIUS_METERS, maxRadiusMeters.coerceAtLeast(1)))

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
        if (update.sequence < 0L || update.source.length > 24) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 위치 측정 정보가 필요합니다.")
        }
    }

    private fun stable(value: String, modulo: Int) = (value.hashCode().toUInt().toLong() % modulo).toInt()
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
