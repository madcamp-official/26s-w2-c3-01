package com.melodybubble.server.profile

import com.melodybubble.server.nearby.NearbyService
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TasteMetric(val label: String, val count: Int, val ratio: Double)
data class TasteFingerprint(
    val genres: List<TasteMetric> = emptyList(),
    val moods: List<TasteMetric> = emptyList(),
)
data class ProfileStats(
    val followingCount: Int,
    val followerCount: Int,
)
data class MelodyAliasProfile(
    val id: String,
    val notes: List<String>,
    val tone: String,
    val mood: String,
    val tempo: Int,
)

data class ProfileTrack(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerTrackId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
)

data class ProfileArtist(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerArtistId: String? = null,
    val name: String,
    val imageUrl: String? = null,
    val genreTags: List<String> = emptyList(),
)

data class ProfilePrivacy(
    val currentMusicVisibility: String = "EVERYONE",
    val listeningInsightsEnabled: Boolean = false,
    val listeningInsightsVisibility: String = "PRIVATE",
)

data class NowPlayingProfile(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionObservedAt: Instant? = null,
    val observedAt: Instant,
    val expiresAt: Instant,
)

data class CommonTasteMetric(
    val label: String,
    val type: String,
    val score: Int,
    val evidenceCount: Int,
)

data class CommonTasteSummary(
    val score: Int,
    val metrics: List<CommonTasteMetric>,
    val algorithmVersion: String = "COMMON_TASTE_V1",
    val sampleSize: Int,
    val calculatedAt: Instant = Instant.now(),
)

data class SharedFollowerPreview(
    val profileHandle: String,
    val displayName: String,
    val avatarUrl: String,
)

data class ProfileResponse(
    val profileHandle: String,
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarSeed: String,
    val avatarUrl: String,
    val genres: List<String>,
    val moods: List<String>,
    val discoverable: Boolean,
    val shareMusic: Boolean,
    val melodyAlias: MelodyAliasProfile?,
    val stats: ProfileStats,
    val tasteFingerprint: TasteFingerprint,
    val profileRevision: Long = 0,
    val signatureTracks: List<ProfileTrack> = emptyList(),
    val favoriteArtists: List<ProfileArtist> = emptyList(),
    val privacy: ProfilePrivacy = ProfilePrivacy(),
)

data class PublicProfileResponse(
    val profileHandle: String,
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarSeed: String,
    val avatarUrl: String,
    val genres: List<String>,
    val moods: List<String>,
    val melodyAlias: MelodyAliasProfile?,
    val stats: ProfileStats,
    val tasteFingerprint: TasteFingerprint,
    val relationship: String,
    val following: Boolean,
    val mutual: Boolean,
    val sharedFollowers: List<SharedFollowerPreview> = emptyList(),
    val sharedFollowerCount: Int = 0,
    val signatureTracks: List<ProfileTrack> = emptyList(),
    val favoriteArtists: List<ProfileArtist> = emptyList(),
    val nowPlaying: NowPlayingProfile? = null,
    val commonTaste: CommonTasteSummary? = null,
    val sectionStates: Map<String, String> = emptyMap(),
)

data class ProfileUpdate(
    val displayName: String,
    val profileColor: String,
    val bio: String = "",
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
)
data class PrivacyUpdate(
    val discoverable: Boolean,
    val shareMusic: Boolean,
)
data class ProfileCurationUpdate(
    val signatureTracks: List<ProfileTrack> = emptyList(),
    val favoriteArtists: List<ProfileArtist> = emptyList(),
    val profileRevision: Long? = null,
)
data class ProfilePrivacyUpdate(
    val currentMusicVisibility: String = "EVERYONE",
    val listeningInsightsEnabled: Boolean = false,
    val listeningInsightsVisibility: String = "PRIVATE",
)
private data class StoredProfile(
    val userId: UUID,
    val profileHandle: String,
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarSeed: String,
    val avatarUrl: String?,
    val genres: List<String>,
    val moods: List<String>,
    val discoverable: Boolean,
    val shareMusic: Boolean,
    val profileRevision: Long,
    val privacy: ProfilePrivacy,
    val melodyAlias: MelodyAliasProfile?,
)

@Service
class ProfileQueryService(
    private val jdbc: JdbcTemplate,
    private val avatars: AvatarUrlFactory,
) {
    fun me(userId: UUID): ProfileResponse {
        val stored = storedByUserId(userId)
        val stats = stats(userId)
        val taste = tasteFingerprint(userId)
        return ProfileResponse(
            profileHandle = stored.profileHandle,
            displayName = stored.displayName,
            profileColor = stored.profileColor,
            bio = stored.bio,
            avatarSeed = stored.avatarSeed,
            avatarUrl = avatars.resolve(stored.avatarSeed, stored.avatarUrl),
            genres = stored.genres,
            moods = stored.moods,
            discoverable = stored.discoverable,
            shareMusic = stored.shareMusic,
            melodyAlias = stored.melodyAlias,
            stats = stats,
            tasteFingerprint = taste,
            profileRevision = stored.profileRevision,
            signatureTracks = signatureTracks(userId),
            favoriteArtists = favoriteArtists(userId),
            privacy = stored.privacy,
        )
    }

    fun publicProfile(viewerId: UUID, profileHandle: String): PublicProfileResponse {
        if (!profileHandle.matches(PROFILE_HANDLE_REGEX)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")
        }
        val target = storedByHandle(profileHandle)
        if (blocked(viewerId, target.userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")
        }
        val following = follows(viewerId, target.userId)
        val followsMe = follows(target.userId, viewerId)
        val mutual = following && followsMe
        val relationship = when {
            viewerId == target.userId -> "SELF"
            mutual -> "MUTUAL"
            following -> "FOLLOWING"
            followsMe -> "FOLLOWS_ME"
            else -> "NONE"
        }
        val currentMusicVisible = canView(
            target.privacy.currentMusicVisibility,
            isSelf = viewerId == target.userId,
            mutual = mutual,
        )
        val nowPlaying = if (currentMusicVisible) nowPlaying(target.userId) else null
        val commonTaste = if (viewerId == target.userId) null else commonTaste(
            viewerId = viewerId,
            targetId = target.userId,
        )
        val sharedFollowers = sharedFollowers(viewerId, target.userId)
        return PublicProfileResponse(
            profileHandle = target.profileHandle,
            displayName = target.displayName,
            profileColor = target.profileColor,
            bio = target.bio,
            avatarSeed = target.avatarSeed,
            avatarUrl = avatars.resolve(target.avatarSeed, target.avatarUrl),
            genres = target.genres,
            moods = target.moods,
            melodyAlias = target.melodyAlias,
            stats = stats(target.userId),
            tasteFingerprint = tasteFingerprint(target.userId, limit = 3),
            relationship = relationship,
            following = following,
            mutual = mutual,
            sharedFollowers = sharedFollowers.previews,
            sharedFollowerCount = sharedFollowers.totalCount,
            signatureTracks = signatureTracks(target.userId),
            favoriteArtists = favoriteArtists(target.userId),
            nowPlaying = nowPlaying,
            commonTaste = commonTaste,
            sectionStates = buildMap {
                put("NOW_PLAYING", when {
                    !currentMusicVisible -> "PRIVATE"
                    nowPlaying == null -> "NO_DATA"
                    else -> "VISIBLE"
                })
                put("COMMON_TASTE", when {
                    viewerId == target.userId -> "NOT_APPLICABLE"
                    commonTaste == null -> "INSUFFICIENT_DATA"
                    else -> "VISIBLE"
                })
            },
        )
    }

    private fun storedByUserId(userId: UUID): StoredProfile = jdbc.query(
        PROFILE_SELECT + " where u.id=?",
        ::storedProfile,
        userId,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")

    private fun storedByHandle(profileHandle: String): StoredProfile = jdbc.query(
        PROFILE_SELECT + " where lower(u.profile_handle)=lower(?)",
        ::storedProfile,
        profileHandle,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")

    private fun storedProfile(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): StoredProfile {
        val notes = runCatching {
            val json = rs.getString("melody_alias_notes").orEmpty()
            Regex("\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(json).map { match ->
                match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
            }.toList()
        }.getOrDefault(emptyList())
        val tempo = rs.getObject("melody_alias_tempo") as? Number
        val aliasId = rs.getString("melody_alias_id")
        val melodyAlias = if (aliasId != null && notes.isNotEmpty() && tempo != null) {
            MelodyAliasProfile(
                aliasId,
                notes,
                rs.getString("melody_alias_tone").orEmpty(),
                rs.getString("melody_alias_mood").orEmpty(),
                tempo.toInt(),
            )
        } else null
        return StoredProfile(
            userId = UUID.fromString(rs.getString("id")),
            profileHandle = rs.getString("profile_handle"),
            displayName = rs.getString("display_name"),
            profileColor = rs.getString("profile_color"),
            bio = rs.getString("bio").orEmpty(),
            avatarSeed = rs.getString("avatar_seed"),
            avatarUrl = rs.getString("avatar_data_url"),
            genres = splitTags(rs.getString("preferred_genres")),
            moods = splitTags(rs.getString("mood_tags")),
            discoverable = rs.getBoolean("discoverable"),
            shareMusic = rs.getBoolean("share_music"),
            profileRevision = rs.getLong("profile_revision"),
            privacy = ProfilePrivacy(
                currentMusicVisibility = rs.getString("current_music_visibility"),
                listeningInsightsEnabled = rs.getBoolean("listening_insights_enabled"),
                listeningInsightsVisibility = rs.getString("listening_insights_visibility"),
            ),
            melodyAlias = melodyAlias,
        )
    }

    private fun stats(userId: UUID): ProfileStats = jdbc.queryForObject(
        """
        select
          (select count(*) from user_follows where follower_id=?),
          (select count(*) from user_follows where followed_id=?)
        """.trimIndent(),
        { rs, _ -> ProfileStats(rs.getInt(1), rs.getInt(2)) },
        userId, userId,
    ) ?: ProfileStats(0, 0)

    private fun tasteFingerprint(userId: UUID, limit: Int = 8): TasteFingerprint {
        val evidence = tasteEvidence(userId)
        return TasteFingerprint(
            genres = evidence.genres.toTasteMetrics(limit),
            moods = evidence.moods.toTasteMetrics(limit),
        )
    }

    private fun Map<String, Double>.toTasteMetrics(limit: Int): List<TasteMetric> {
        val sorted = entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).take(limit)
        val total = sorted.sumOf { it.value }.coerceAtLeast(1.0)
        return sorted.map { (label, weight) ->
            TasteMetric(label, weight.toInt().coerceAtLeast(1), (weight / total).roundRatio())
        }
    }

    private fun signatureTracks(userId: UUID): List<ProfileTrack> = jdbc.query(
        """
        select rank,provider,provider_track_id,title,artist_name,album_name,artwork_url,genre_tags,mood_tags
        from profile_signature_tracks where user_id=? order by rank
        """.trimIndent(),
        { rs, _ ->
            ProfileTrack(
                rank = rs.getInt("rank"),
                provider = rs.getString("provider"),
                providerTrackId = rs.getString("provider_track_id"),
                title = rs.getString("title"),
                artist = rs.getString("artist_name"),
                album = rs.getString("album_name"),
                artworkUrl = rs.getString("artwork_url"),
                genreTags = splitTags(rs.getString("genre_tags")),
                moodTags = splitTags(rs.getString("mood_tags")),
            )
        },
        userId,
    )

    private fun favoriteArtists(userId: UUID): List<ProfileArtist> = jdbc.query(
        """
        select rank,provider,provider_artist_id,artist_name,image_url,genre_tags
        from profile_favorite_artists where user_id=? order by rank
        """.trimIndent(),
        { rs, _ ->
            ProfileArtist(
                rank = rs.getInt("rank"),
                provider = rs.getString("provider"),
                providerArtistId = rs.getString("provider_artist_id"),
                name = rs.getString("artist_name"),
                imageUrl = rs.getString("image_url"),
                genreTags = splitTags(rs.getString("genre_tags")),
            )
        },
        userId,
    )

    private fun nowPlaying(userId: UUID): NowPlayingProfile? = jdbc.query(
        """
        select track_title,artist_name,album_name,album_art_url,is_playing,duration_ms,position_ms,
          position_observed_at,observed_at,expires_at
        from music_statuses
        where user_id=? and is_playing=true and expires_at>now()
        """.trimIndent(),
        { rs, _ ->
            NowPlayingProfile(
                title = rs.getString("track_title"),
                artist = rs.getString("artist_name"),
                album = rs.getString("album_name"),
                artworkUrl = rs.getString("album_art_url"),
                isPlaying = rs.getBoolean("is_playing"),
                durationMs = (rs.getObject("duration_ms") as? Number)?.toLong(),
                positionMs = (rs.getObject("position_ms") as? Number)?.toLong(),
                positionObservedAt = rs.getTimestamp("position_observed_at")?.toInstant(),
                observedAt = rs.getTimestamp("observed_at").toInstant(),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
            )
        },
        userId,
    ).firstOrNull()

    private fun commonTaste(
        viewerId: UUID,
        targetId: UUID,
    ): CommonTasteSummary? {
        val viewer = tasteEvidence(viewerId)
        val target = tasteEvidence(targetId)
        if (viewer.evidenceCount < 3 || target.evidenceCount < 3) return null

        val genreScore = weightedJaccard(viewer.genres, target.genres)
        val moodScore = weightedJaccard(viewer.moods, target.moods)
        val artistScore = weightedJaccard(viewer.artists, target.artists)
        val trackScore = weightedJaccard(viewer.tracks, target.tracks)
        val total = (
            genreScore * 0.35 + moodScore * 0.35 + artistScore * 0.20 + trackScore * 0.10
            ).toInt().coerceIn(0, 100)

        val metrics = buildList {
            addAll(overlapMetrics(viewer.genres, target.genres, "GENRE"))
            addAll(overlapMetrics(viewer.moods, target.moods, "MOOD"))
            if (size < 4) addAll(overlapMetrics(viewer.artists, target.artists, "ARTIST"))
        }.sortedWith(
            compareByDescending<CommonTasteMetric> { it.score }
                .thenByDescending { it.evidenceCount }
                .thenBy { it.label },
        ).distinctBy { it.type to it.label.lowercase() }.take(4)

        return CommonTasteSummary(
            score = total,
            metrics = metrics,
            sampleSize = viewer.evidenceCount + target.evidenceCount,
        )
    }

    private fun tasteEvidence(userId: UUID): TasteEvidence {
        val profile = storedByUserId(userId)
        val tracks = signatureTracks(userId)
        val artists = favoriteArtists(userId)
        val genres = linkedMapOf<String, Double>()
        val moods = linkedMapOf<String, Double>()
        val artistWeights = linkedMapOf<String, Double>()
        val trackWeights = linkedMapOf<String, Double>()

        profile.genres.forEach { genres.addWeight(it, 2.0) }
        profile.moods.forEach { moods.addWeight(it, 2.0) }
        tracks.forEach { track ->
            track.genreTags.forEach { genres.addWeight(it, 1.5) }
            track.moodTags.forEach { moods.addWeight(it, 1.5) }
            artistWeights.addWeight(track.artist, 0.75)
            trackWeights.addWeight("${track.title} · ${track.artist}", 1.0)
        }
        artists.forEach { artist ->
            artist.genreTags.forEach { genres.addWeight(it, 1.0) }
            artistWeights.addWeight(artist.name, 1.5)
        }
        return TasteEvidence(genres, moods, artistWeights, trackWeights)
    }

    private fun weightedJaccard(left: Map<String, Double>, right: Map<String, Double>): Double {
        val keys = left.keys + right.keys
        if (keys.isEmpty()) return 0.0
        val denominator = keys.sumOf { maxOf(left[it] ?: 0.0, right[it] ?: 0.0) }
        if (denominator <= 0.0) return 0.0
        return keys.sumOf { minOf(left[it] ?: 0.0, right[it] ?: 0.0) } / denominator * 100.0
    }

    private fun overlapMetrics(
        left: Map<String, Double>,
        right: Map<String, Double>,
        type: String,
    ): List<CommonTasteMetric> = left.keys.intersect(right.keys).mapNotNull { key ->
        val leftWeight = left[key] ?: return@mapNotNull null
        val rightWeight = right[key] ?: return@mapNotNull null
        val largest = maxOf(leftWeight, rightWeight)
        if (largest <= 0.0) return@mapNotNull null
        CommonTasteMetric(
            label = key,
            type = type,
            score = ((minOf(leftWeight, rightWeight) / largest) * 100).toInt().coerceIn(0, 100),
            evidenceCount = minOf(leftWeight, rightWeight).toInt().coerceAtLeast(1),
        )
    }

    private fun MutableMap<String, Double>.addWeight(rawLabel: String, weight: Double) {
        val label = rawLabel.trim().take(80)
        if (label.isBlank()) return
        val existingKey = keys.firstOrNull { it.equals(label, ignoreCase = true) } ?: label
        this[existingKey] = (this[existingKey] ?: 0.0) + weight
    }

    private fun canView(scope: String, isSelf: Boolean, mutual: Boolean): Boolean = when {
        isSelf -> true
        scope == "EVERYONE" -> true
        scope == "MUTUALS" -> mutual
        else -> false
    }

    private data class SharedFollowers(
        val totalCount: Int,
        val previews: List<SharedFollowerPreview>,
    )

    private fun sharedFollowers(viewerId: UUID, targetId: UUID): SharedFollowers {
        if (viewerId == targetId) return SharedFollowers(0, emptyList())
        val rows = jdbc.query(
            """
            select u.profile_handle, u.display_name, u.avatar_seed, u.avatar_data_url, count(*) over()::int
            from user_follows viewer_following
            join users u on u.id=viewer_following.followed_id
            join user_follows target_followers
              on target_followers.follower_id=u.id and target_followers.followed_id=?
            where viewer_following.follower_id=?
              and not exists(
                select 1 from user_blocks b
                where (b.blocker_id=? and b.blocked_id=u.id)
                   or (b.blocker_id=u.id and b.blocked_id=?)
              )
            order by viewer_following.created_at desc, u.display_name asc
            limit 3
            """.trimIndent(),
            { rs, _ ->
                rs.getInt(5) to SharedFollowerPreview(
                    profileHandle = rs.getString(1),
                    displayName = rs.getString(2),
                    avatarUrl = avatars.resolve(rs.getString(3), rs.getString(4)),
                )
            },
            targetId,
            viewerId,
            viewerId,
            viewerId,
        )
        return SharedFollowers(
            totalCount = rows.firstOrNull()?.first ?: 0,
            previews = rows.map(Pair<Int, SharedFollowerPreview>::second),
        )
    }

    private fun follows(from: UUID, to: UUID): Boolean = from != to && jdbc.queryForObject(
        "select exists(select 1 from user_follows where follower_id=? and followed_id=?)",
        Boolean::class.java,
        from,
        to,
    ) == true

    private fun blocked(left: UUID, right: UUID): Boolean = left != right && jdbc.queryForObject(
        """select exists(select 1 from user_blocks where
           (blocker_id=? and blocked_id=?) or (blocker_id=? and blocked_id=?))""",
        Boolean::class.java,
        left, right, right, left,
    ) == true

    private fun splitTags(value: String?) = value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
    private fun Double.roundRatio() = kotlin.math.round(this * 1000.0) / 1000.0

    private data class TasteEvidence(
        val genres: Map<String, Double>,
        val moods: Map<String, Double>,
        val artists: Map<String, Double>,
        val tracks: Map<String, Double>,
    ) {
        val evidenceCount: Int
            get() = genres.size + moods.size + artists.size + tracks.size
    }

    companion object {
        private val PROFILE_HANDLE_REGEX = Regex("[a-z0-9_]{3,32}")
        private val PROFILE_SELECT = """
            select u.id,u.profile_handle,u.display_name,u.profile_color,u.bio,u.avatar_seed,u.avatar_data_url,
              u.preferred_genres,u.mood_tags,u.melody_alias_id,
              u.melody_alias_notes::text melody_alias_notes,u.melody_alias_tone,u.melody_alias_mood,
              u.melody_alias_tempo,u.profile_revision,coalesce(p.discoverable,true) discoverable,
              coalesce(p.share_music,true) share_music,
              coalesce(p.current_music_visibility,'EVERYONE') current_music_visibility,
              coalesce(p.listening_insights_enabled,false) listening_insights_enabled,
              coalesce(p.listening_insights_visibility,'PRIVATE') listening_insights_visibility
            from users u left join user_privacy_settings p on p.user_id=u.id
        """.trimIndent()
    }
}

@RestController
@RequestMapping("/api/v1/me")
class ProfileController(
    private val jdbc: JdbcTemplate,
    private val nearby: NearbyService,
    private val rateLimiter: ActionRateLimiter,
    private val profiles: ProfileQueryService,
    private val avatars: AvatarUrlFactory,
) {
    @GetMapping fun me(principal: Principal): ProfileResponse = profiles.me(principal.userId())

    @PatchMapping
    fun update(principal: Principal, @RequestBody request: ProfileUpdate): ProfileResponse {
        val userId = principal.userId()
        val name = request.displayName.trim().take(40)
        val color = request.profileColor.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#6750A4"
        require(name.length >= 2) { "Display name must contain at least 2 characters" }
        jdbc.update(
            """update users set display_name=?,profile_color=?,bio=?,preferred_genres=?,mood_tags=?,
               profile_revision=profile_revision+1,updated_at=now() where id=?""",
            name, color, request.bio.trim().take(160),
            request.genres.cleanTags().joinToString(","), request.moods.cleanTags().joinToString(","), userId,
        )
        return profiles.me(userId)
    }

    @PostMapping("/avatar/randomize")
    fun randomizeAvatar(principal: Principal): ProfileResponse {
        val userId = principal.userId()
        rateLimiter.enforce(userId, "AVATAR_RANDOMIZE", 10, Duration.ofMinutes(1))
        jdbc.update(
            "update users set avatar_seed=?,avatar_data_url=null,profile_revision=profile_revision+1,updated_at=now() where id=?",
            UUID.randomUUID().toString(),
            userId,
        )
        return profiles.me(userId)
    }

    @PutMapping("/avatar")
    fun customizeAvatar(principal: Principal, @RequestBody request: AvatarCustomization): ProfileResponse {
        val userId = principal.userId()
        rateLimiter.enforce(userId, "AVATAR_CUSTOMIZE", 30, Duration.ofMinutes(1))
        val customization = runCatching { request.validated() }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.message, it) }
        val seed = jdbc.queryForObject("select avatar_seed from users where id=?", String::class.java, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")
        jdbc.update(
            "update users set avatar_data_url=?,profile_revision=profile_revision+1,updated_at=now() where id=?",
            avatars.create(seed, customization), userId,
        )
        return profiles.me(userId)
    }

    @PutMapping("/privacy")
    @Transactional
    fun privacy(principal: Principal, @RequestBody request: PrivacyUpdate): ProfileResponse {
        val userId = principal.userId()
        val previousAudience = nearby.musicAudienceSnapshot(userId)
        jdbc.update(
            """insert into user_privacy_settings(user_id,discoverable,share_music)
               values (?,?,?) on conflict(user_id) do update set discoverable=excluded.discoverable,
               share_music=excluded.share_music,
               updated_at=now()""",
            userId, request.discoverable, request.shareMusic,
        )
        jdbc.update(
            """
            update user_privacy_settings set
              discoverability_scope=case when ? then case when discoverability_scope='HIDDEN' then 'NEARBY' else discoverability_scope end else 'HIDDEN' end,
              music_visibility=case when ? then case when music_visibility='HIDDEN' then 'TITLE_ARTIST' else music_visibility end else 'HIDDEN' end,
              current_music_visibility=case when ? then case when current_music_visibility='PRIVATE' then 'EVERYONE' else current_music_visibility end else 'PRIVATE' end,
              updated_at=now() where user_id=?
            """.trimIndent(),
            request.discoverable, request.shareMusic, request.shareMusic, userId,
        )
        nearby.publishPrivacyAudienceChangesAfterCommit(userId, previousAudience)
        return profiles.me(userId)
    }

    @PutMapping("/profile-curation")
    @Transactional
    fun curation(principal: Principal, @RequestBody request: ProfileCurationUpdate): ProfileResponse {
        val userId = principal.userId()
        require(request.signatureTracks.size <= 3) { "At most 3 signature tracks are allowed" }
        require(request.favoriteArtists.size <= 3) { "At most 3 favorite artists are allowed" }
        request.profileRevision?.let { expected ->
            val actual = jdbc.queryForObject(
                "select profile_revision from users where id=?",
                Long::class.java,
                userId,
            ) ?: 0L
            if (expected != actual) throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "다른 기기에서 프로필이 변경되었습니다. 새로고침 후 다시 시도해 주세요.",
            )
        }

        val tracks = request.signatureTracks.mapIndexed { index, track ->
            track.copy(
                rank = index + 1,
                provider = track.provider.cleanProvider(),
                providerTrackId = track.providerTrackId.cleanOptional(160),
                title = track.title.cleanRequired("Track title", 160),
                artist = track.artist.cleanRequired("Track artist", 160),
                album = track.album.cleanOptional(160),
                artworkUrl = track.artworkUrl.cleanHttpsUrl(),
                genreTags = track.genreTags.cleanTags(),
                moodTags = track.moodTags.cleanTags(),
            )
        }
        val artists = request.favoriteArtists.mapIndexed { index, artist ->
            artist.copy(
                rank = index + 1,
                provider = artist.provider.cleanProvider(),
                providerArtistId = artist.providerArtistId.cleanOptional(160),
                name = artist.name.cleanRequired("Artist name", 160),
                imageUrl = artist.imageUrl.cleanHttpsUrl(),
                genreTags = artist.genreTags.cleanTags(),
            )
        }

        jdbc.update("delete from profile_signature_tracks where user_id=?", userId)
        tracks.forEach { track ->
            jdbc.update(
                """
                insert into profile_signature_tracks(
                  user_id,rank,provider,provider_track_id,title,artist_name,album_name,artwork_url,genre_tags,mood_tags
                ) values (?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
                userId, track.rank, track.provider, track.providerTrackId, track.title, track.artist,
                track.album, track.artworkUrl, track.genreTags.joinToString(","), track.moodTags.joinToString(","),
            )
        }
        jdbc.update("delete from profile_favorite_artists where user_id=?", userId)
        artists.forEach { artist ->
            jdbc.update(
                """
                insert into profile_favorite_artists(
                  user_id,rank,provider,provider_artist_id,artist_name,image_url,genre_tags
                ) values (?,?,?,?,?,?,?)
                """.trimIndent(),
                userId, artist.rank, artist.provider, artist.providerArtistId, artist.name,
                artist.imageUrl, artist.genreTags.joinToString(","),
            )
        }
        jdbc.update("update users set profile_revision=profile_revision+1,updated_at=now() where id=?", userId)
        return profiles.me(userId)
    }

    @PutMapping("/profile-privacy")
    @Transactional
    fun profilePrivacy(principal: Principal, @RequestBody request: ProfilePrivacyUpdate): ProfileResponse {
        val userId = principal.userId()
        val currentMusic = request.currentMusicVisibility.requireOneOf(
            "currentMusicVisibility", setOf("EVERYONE", "MUTUALS", "PRIVATE"),
        )
        val listening = request.listeningInsightsVisibility.requireOneOf(
            "listeningInsightsVisibility", setOf("EVERYONE", "MUTUALS", "PRIVATE"),
        )
        val previousAudience = nearby.musicAudienceSnapshot(userId)
        jdbc.update(
            """
            insert into user_privacy_settings(
              user_id,current_music_visibility,listening_insights_enabled,listening_insights_visibility,
              music_visibility,share_music
            ) values (?,?,?,?,?,?)
            on conflict(user_id) do update set
              current_music_visibility=excluded.current_music_visibility,
              listening_insights_enabled=excluded.listening_insights_enabled,
              listening_insights_visibility=excluded.listening_insights_visibility,
              music_visibility=excluded.music_visibility,
              share_music=excluded.share_music,
              updated_at=now()
            """.trimIndent(),
            userId, currentMusic, request.listeningInsightsEnabled, listening,
            currentMusic.toLegacyMusicVisibility(), currentMusic != "PRIVATE",
        )
        nearby.publishPrivacyAudienceChangesAfterCommit(userId, previousAudience)
        return profiles.me(userId)
    }

    private fun List<String>.cleanTags() = map(String::trim).filter(String::isNotBlank).distinct().take(8)
    private fun String.cleanProvider() = trim().uppercase().take(32).ifBlank { "MANUAL" }
    private fun String?.cleanOptional(limit: Int) = this?.trim()?.take(limit)?.ifBlank { null }
    private fun String.cleanRequired(label: String, limit: Int): String = trim().take(limit).also {
        require(it.isNotBlank()) { "$label is required" }
    }
    private fun String?.cleanHttpsUrl(): String? = cleanOptional(2_000)?.also {
        require(it.startsWith("https://")) { "Media URL must use HTTPS" }
    }
    private fun String.requireOneOf(label: String, allowed: Set<String>): String = trim().uppercase().also {
        require(it in allowed) { "$label is invalid" }
    }
    private fun String.toLegacyMusicVisibility() = when (this) {
        "MUTUALS" -> "MUTUALS"
        "PRIVATE" -> "HIDDEN"
        else -> "TITLE_ARTIST"
    }
}

@RestController
@RequestMapping("/api/v1/profiles")
class PublicProfileController(private val profiles: ProfileQueryService) {
    @GetMapping("/{profileHandle}")
    fun profile(principal: Principal, @PathVariable profileHandle: String): PublicProfileResponse =
        profiles.publicProfile(principal.userId(), profileHandle)
}

private fun Principal.userId() = UUID.fromString(name)
