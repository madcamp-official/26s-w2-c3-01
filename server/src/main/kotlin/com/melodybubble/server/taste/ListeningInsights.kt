package com.melodybubble.server.taste

import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class ListenEventInput(
    val clientEventId: UUID,
    val title: String,
    val artist: String,
    val album: String? = null,
    val provider: String? = null,
    val providerTrackId: String? = null,
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val sourcePackage: String? = null,
    val startedAt: Instant,
    val endedAt: Instant,
    val playedMs: Long,
    val durationMs: Long? = null,
    val completionRatio: Double? = null,
)

data class ListenEventBatch(val events: List<ListenEventInput> = emptyList())
data class ListenEventBatchResult(
    val collectionEnabled: Boolean,
    val acceptedCount: Int,
    val duplicateCount: Int,
)

@Service
class ListeningInsightsService(
    private val jdbc: JdbcTemplate,
    private val tasteMatches: TasteMatchService,
) {
    @Transactional
    fun record(userId: UUID, batch: ListenEventBatch): ListenEventBatchResult {
        require(batch.events.size <= MAX_BATCH_SIZE) { "At most $MAX_BATCH_SIZE listen events are allowed" }
        val enabled = jdbc.queryForObject(
            "select coalesce(listening_insights_enabled,false) from user_privacy_settings where user_id=?",
            Boolean::class.java,
            userId,
        ) == true
        if (!enabled || batch.events.isEmpty()) {
            return ListenEventBatchResult(enabled, acceptedCount = 0, duplicateCount = 0)
        }

        var accepted = 0
        batch.events.forEach { raw ->
            val event = raw.validated()
            val canonicalKey = canonicalTrackKey(event)
            val trackId = upsertTrack(canonicalKey, event)
            accepted += jdbc.update(
                """
                insert into music_listen_events(
                  user_id,client_event_id,track_id,canonical_key,title,artist_name,album_name,
                  provider,provider_track_id,source_type,source_package,started_at,ended_at,
                  played_ms,duration_ms,completion_ratio
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(user_id,client_event_id) do nothing
                """.trimIndent(),
                userId,
                event.clientEventId,
                trackId,
                canonicalKey,
                event.title,
                event.artist,
                event.album,
                event.provider,
                event.providerTrackId,
                event.sourceType,
                event.sourcePackage,
                event.startedAt,
                event.endedAt,
                event.playedMs,
                event.durationMs,
                event.completionRatio,
            )
        }
        if (accepted > 0) tasteMatches.markDirty(userId, "LISTEN_EVENTS")
        return ListenEventBatchResult(
            collectionEnabled = true,
            acceptedCount = accepted,
            duplicateCount = batch.events.size - accepted,
        )
    }

    @Transactional
    fun clear(userId: UUID) {
        jdbc.update("delete from music_listen_events where user_id=?", userId)
        jdbc.update("delete from taste_user_embeddings where user_id=?", userId)
        tasteMatches.markDirty(userId, "LISTEN_EVENTS_CLEARED")
    }

    private fun upsertTrack(canonicalKey: String, event: ListenEventInput): UUID = jdbc.query(
        """
        insert into taste_track_catalog(
          canonical_key,provider,provider_track_id,title,artist_name,album_name,duration_ms
        ) values (?,?,?,?,?,?,?)
        on conflict(canonical_key) do update set
          provider=coalesce(taste_track_catalog.provider,excluded.provider),
          provider_track_id=coalesce(taste_track_catalog.provider_track_id,excluded.provider_track_id),
          album_name=coalesce(taste_track_catalog.album_name,excluded.album_name),
          duration_ms=coalesce(taste_track_catalog.duration_ms,excluded.duration_ms),
          updated_at=now()
        returning id
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString("id")) },
        canonicalKey,
        event.provider,
        event.providerTrackId,
        event.title,
        event.artist,
        event.album,
        event.durationMs,
    ).single()

    private fun ListenEventInput.validated(): ListenEventInput {
        val now = Instant.now()
        val safeTitle = title.cleanRequired("Track title", 160)
        val safeArtist = artist.cleanRequired("Track artist", 160)
        val safeAlbum = album.cleanOptional(160)
        val safeProvider = provider.cleanOptional(32)?.uppercase(Locale.ROOT)
        val safeProviderTrackId = providerTrackId.cleanOptional(160)
        val safeSource = sourceType.cleanRequired("Source type", 32).uppercase(Locale.ROOT)
        val safePackage = sourcePackage.cleanOptional(160)
        require(startedAt >= now.minus(MAX_EVENT_AGE) && startedAt <= now.plusSeconds(60)) {
            "Listen event start time is invalid"
        }
        require(endedAt >= startedAt && endedAt <= now.plusSeconds(60)) { "Listen event end time is invalid" }
        require(playedMs in MIN_PLAYED_MILLIS..MAX_MEDIA_MILLIS) { "Listen event is too short or invalid" }
        require(durationMs == null || durationMs in 1..MAX_MEDIA_MILLIS) { "Track duration is invalid" }
        val ratio = completionRatio ?: durationMs?.let { playedMs.toDouble() / it.toDouble() }
        require(ratio == null || ratio.isFinite()) { "Completion ratio is invalid" }
        return copy(
            title = safeTitle,
            artist = safeArtist,
            album = safeAlbum,
            provider = safeProvider,
            providerTrackId = safeProviderTrackId,
            sourceType = safeSource,
            sourcePackage = safePackage,
            completionRatio = ratio?.coerceIn(0.0, 1.0),
        )
    }

    private fun canonicalTrackKey(event: ListenEventInput): String {
        if (!event.provider.isNullOrBlank() && !event.providerTrackId.isNullOrBlank()) {
            return "provider:${event.provider.lowercase()}:${event.providerTrackId.lowercase()}"
        }
        val durationBucket = event.durationMs?.div(1_000L)?.toString() ?: "unknown"
        return "metadata:${normalizeIdentity(event.artist)}:${normalizeIdentity(event.title)}:$durationBucket"
            .take(400)
    }

    private fun normalizeIdentity(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(IDENTITY_NOISE, "")

    private fun String.cleanRequired(label: String, limit: Int): String = trim().take(limit).also {
        require(it.isNotBlank()) { "$label is required" }
    }

    private fun String?.cleanOptional(limit: Int): String? = this?.trim()?.take(limit)?.ifBlank { null }

    private companion object {
        const val MAX_BATCH_SIZE = 50
        const val MIN_PLAYED_MILLIS = 10_000L
        const val MAX_MEDIA_MILLIS = 86_400_000L
        val MAX_EVENT_AGE: Duration = Duration.ofDays(90)
        val IDENTITY_NOISE = Regex("[^\\p{L}\\p{N}]+")
    }
}

@RestController
@RequestMapping("/api/v1/taste/listens")
class ListeningInsightsController(
    private val service: ListeningInsightsService,
    private val rateLimiter: ActionRateLimiter,
) {
    @PostMapping("/batch")
    fun record(principal: Principal, @RequestBody batch: ListenEventBatch): ListenEventBatchResult {
        val userId = UUID.fromString(principal.name)
        rateLimiter.enforce(userId, "TASTE_LISTEN_BATCH", 30, Duration.ofMinutes(1))
        return service.record(userId, batch)
    }

    @DeleteMapping
    fun clear(principal: Principal) = service.clear(UUID.fromString(principal.name))
}

data class TasteFeedbackInput(
    val clientFeedbackId: UUID,
    val targetProfileHandle: String,
    val action: String,
)

@Service
class TasteFeedbackService(private val jdbc: JdbcTemplate) {
    @Transactional
    fun record(actorUserId: UUID, input: TasteFeedbackInput) {
        val action = input.action.trim().uppercase(Locale.ROOT)
        val strength = ACTION_STRENGTH[action]
            ?: throw IllegalArgumentException("Unsupported taste feedback action")
        val targetUserId = jdbc.query(
            "select id from users where profile_handle=?",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            input.targetProfileHandle.trim().lowercase(Locale.ROOT),
        ).singleOrNull() ?: throw IllegalArgumentException("Target profile was not found")
        require(targetUserId != actorUserId) { "Self feedback is not allowed" }
        val exposureId = jdbc.query(
            """
            select id from taste_match_exposures
            where viewer_user_id=? and target_user_id=? and created_at>now()-interval '24 hours'
            order by created_at desc limit 1
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            actorUserId,
            targetUserId,
        ).singleOrNull()
        jdbc.update(
            """
            insert into taste_match_feedback(
              client_feedback_id,exposure_id,actor_user_id,target_user_id,action,strength
            ) values (?,?,?,?,?,?)
            on conflict(actor_user_id,client_feedback_id) do nothing
            """.trimIndent(),
            input.clientFeedbackId,
            exposureId,
            actorUserId,
            targetUserId,
            action,
            strength,
        )
    }

    private companion object {
        val ACTION_STRENGTH = mapOf(
            "PROFILE_OPEN" to 0.15,
            "PREVIEW_PLAY" to 0.35,
            "MUSIC_APP_OPEN" to 0.55,
            "SAME_TASTE" to 0.7,
            "FOLLOW" to 0.8,
            "MUTUAL_FOLLOW" to 1.0,
            "CHAT" to 1.0,
            "BLOCK" to -1.0,
        )
    }
}

@RestController
@RequestMapping("/api/v1/taste/feedback")
class TasteFeedbackController(
    private val service: TasteFeedbackService,
    private val rateLimiter: ActionRateLimiter,
) {
    @PostMapping
    fun record(principal: Principal, @RequestBody input: TasteFeedbackInput) {
        val userId = UUID.fromString(principal.name)
        rateLimiter.enforce(userId, "TASTE_FEEDBACK", 60, Duration.ofMinutes(1))
        service.record(userId, input)
    }
}
