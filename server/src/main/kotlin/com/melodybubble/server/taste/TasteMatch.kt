package com.melodybubble.server.taste

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

data class TasteMatchMetric(
    val label: String,
    val type: String,
    val score: Int,
    val evidenceCount: Int,
)

data class TasteMatchSummary(
    val score: Int?,
    val confidence: String,
    val metrics: List<TasteMatchMetric>,
    val algorithmVersion: String = TasteEmbeddingAlgorithm.ALGORITHM_VERSION,
    val sampleSize: Int,
    val calculatedAt: Instant = Instant.now(),
)

internal data class TasteSignal(
    val label: String,
    val type: String,
    val weight: Double,
)

internal data class TasteVector(
    val values: DoubleArray,
    val signals: Map<String, TasteSignal>,
) {
    val evidenceCount: Int get() = signals.size
}

/**
 * One symmetric taste matcher shared by profile and nearby surfaces.
 *
 * The bootstrap encoder uses the exact same two-tower contract as the learned model: both users
 * pass through one shared encoder and are compared with cosine similarity. Explicit selections
 * and consented listening events make the first production vector useful before enough interaction
 * data exists to train and activate an ONNX artifact. Learned embeddings can later be stored in
 * taste_user_embeddings without changing the public score contract.
 */
@Service
class TasteMatchService(private val jdbc: JdbcTemplate) {
    private val matchCache = ConcurrentHashMap<MatchCacheKey, CachedMatch>()

    fun match(leftUserId: UUID, rightUserId: UUID, context: String? = null): TasteMatchSummary? =
        matchMany(leftUserId, listOf(rightUserId), context)[rightUserId]

    fun matchMany(
        viewerUserId: UUID,
        targetUserIds: Collection<UUID>,
        context: String? = null,
    ): Map<UUID, TasteMatchSummary> {
        val distinctTargets = targetUserIds.filter { it != viewerUserId }.distinct()
        if (distinctTargets.isEmpty()) return emptyMap()
        val now = System.nanoTime()
        val matches = linkedMapOf<UUID, TasteMatchSummary>()
        val missingTargets = distinctTargets.filter { targetId ->
            val cached = matchCache[MatchCacheKey(viewerUserId, targetId)]
            if (cached != null && cached.expiresAtNanos > now) {
                matches[targetId] = cached.summary
                false
            } else true
        }
        if (missingTargets.isNotEmpty()) {
            val users = listOf(viewerUserId) + missingTargets
            val signals = loadSignals(users, viewerUserId)
            val vectors = users.associateWith { userId ->
                TasteEmbeddingAlgorithm.encode(signals[userId].orEmpty())
            }
            val viewer = vectors.getValue(viewerUserId)
            missingTargets.forEach { targetId ->
                val summary = TasteEmbeddingAlgorithm.compare(viewer, vectors.getValue(targetId))
                matches[targetId] = summary
                matchCache[MatchCacheKey(viewerUserId, targetId)] = CachedMatch(
                    summary,
                    now + MATCH_CACHE_TTL_NANOS,
                )
            }
        }
        context?.uppercase()?.takeIf { it in EXPOSURE_CONTEXTS }?.let { safeContext ->
            logExposures(viewerUserId, matches, safeContext)
        }
        return distinctTargets.associateWith { matches.getValue(it) }
    }

    fun markDirty(userId: UUID, reason: String) {
        matchCache.keys.removeIf { it.viewerUserId == userId || it.targetUserId == userId }
        jdbc.update(
            """
            insert into taste_embedding_jobs(user_id,reason,requested_at,claimed_at,last_error)
            values (?,?,now(),null,null)
            on conflict(user_id) do update set
              reason=excluded.reason,requested_at=now(),claimed_at=null,last_error=null
            """.trimIndent(),
            userId,
            reason.trim().uppercase().take(40).ifBlank { "UNKNOWN" },
        )
    }

    fun refreshDirtyBatch(limit: Int = 50) {
        val pending = jdbc.query(
            """
            select user_id from taste_embedding_jobs
            where claimed_at is null or claimed_at<now()-interval '10 minutes'
            order by requested_at limit ?
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("user_id")) },
            limit.coerceIn(1, 200),
        )
        pending.forEach { userId ->
            val claimed = jdbc.update(
                """
                update taste_embedding_jobs set claimed_at=now(),attempts=attempts+1,last_error=null
                where user_id=? and (claimed_at is null or claimed_at<now()-interval '10 minutes')
                """.trimIndent(),
                userId,
            )
            if (claimed == 0) return@forEach
            runCatching { persistBootstrapEmbedding(userId) }
                .onSuccess { jdbc.update("delete from taste_embedding_jobs where user_id=?", userId) }
                .onFailure { error ->
                    jdbc.update(
                        "update taste_embedding_jobs set claimed_at=null,last_error=? where user_id=?",
                        (error.message ?: error.javaClass.simpleName).take(500),
                        userId,
                    )
                }
        }
    }

    private fun persistBootstrapEmbedding(userId: UUID) {
        val vector = TasteEmbeddingAlgorithm.encode(loadSignals(listOf(userId), userId)[userId].orEmpty())
        val confidence = TasteEmbeddingAlgorithm.confidenceForEvidence(vector.evidenceCount)
        val arrayLiteral = vector.values.joinToString(prefix = "{", postfix = "}", separator = ",")
        jdbc.update(
            """
            insert into taste_user_embeddings(
              user_id,model_version,algorithm_version,embedding,evidence_count,confidence,source_mask
            ) values (?,'taste-bootstrap-v2',?,cast(? as real[]),?,?,0)
            on conflict(user_id,model_version) do update set
              algorithm_version=excluded.algorithm_version,embedding=excluded.embedding,
              evidence_count=excluded.evidence_count,confidence=excluded.confidence,calculated_at=now()
            """.trimIndent(),
            userId,
            TasteEmbeddingAlgorithm.ALGORITHM_VERSION,
            arrayLiteral,
            vector.evidenceCount,
            confidence,
        )
    }

    private fun loadSignals(userIds: List<UUID>, viewerUserId: UUID): Map<UUID, List<TasteSignal>> {
        val result = userIds.associateWith { mutableListOf<TasteSignal>() }.toMutableMap()
        val placeholders = userIds.joinToString(",") { "?" }
        val args = userIds.toTypedArray()

        jdbc.query(
            "select id,preferred_genres,mood_tags from users where id in ($placeholders)",
            { rs ->
                val userId = UUID.fromString(rs.getString("id"))
                splitTags(rs.getString("preferred_genres")).forEach {
                    result.getValue(userId).add(TasteSignal(it, "GENRE", 2.0))
                }
                splitTags(rs.getString("mood_tags")).forEach {
                    result.getValue(userId).add(TasteSignal(it, "MOOD", 2.0))
                }
            },
            *args,
        )
        jdbc.query(
            """
            select user_id,title,artist_name,genre_tags,mood_tags
            from profile_signature_tracks where user_id in ($placeholders)
            """.trimIndent(),
            { rs ->
                val userId = UUID.fromString(rs.getString("user_id"))
                val bucket = result.getValue(userId)
                splitTags(rs.getString("genre_tags")).forEach { bucket.add(TasteSignal(it, "GENRE", 1.5)) }
                splitTags(rs.getString("mood_tags")).forEach { bucket.add(TasteSignal(it, "MOOD", 1.5)) }
                bucket.add(TasteSignal(rs.getString("artist_name"), "ARTIST", 0.75))
                bucket.add(
                    TasteSignal(
                        "${rs.getString("title")} · ${rs.getString("artist_name")}",
                        "TRACK",
                        1.0,
                    )
                )
            },
            *args,
        )
        jdbc.query(
            """
            select user_id,artist_name,genre_tags
            from profile_favorite_artists where user_id in ($placeholders)
            """.trimIndent(),
            { rs ->
                val userId = UUID.fromString(rs.getString("user_id"))
                val bucket = result.getValue(userId)
                splitTags(rs.getString("genre_tags")).forEach { bucket.add(TasteSignal(it, "GENRE", 1.0)) }
                bucket.add(TasteSignal(rs.getString("artist_name"), "ARTIST", 1.5))
            },
            *args,
        )
        val usersAllowedToShareListening = mutableSetOf<UUID>()
        jdbc.query(
            """
            select privacy.user_id,privacy.listening_insights_enabled,
              privacy.listening_insights_visibility,
              exists(select 1 from user_follows f where f.follower_id=? and f.followed_id=privacy.user_id)
                and exists(select 1 from user_follows f where f.follower_id=privacy.user_id and f.followed_id=?) is_mutual
            from user_privacy_settings privacy
            where privacy.user_id in ($placeholders)
            """.trimIndent(),
            { rs ->
                val userId = UUID.fromString(rs.getString("user_id"))
                val enabled = rs.getBoolean("listening_insights_enabled")
                val visibility = rs.getString("listening_insights_visibility") ?: "PRIVATE"
                val visible = userId == viewerUserId ||
                    visibility == "EVERYONE" ||
                    (visibility == "MUTUALS" && rs.getBoolean("is_mutual"))
                if (enabled && visible) usersAllowedToShareListening += userId
            },
            viewerUserId,
            viewerUserId,
            *args,
        )
        if (usersAllowedToShareListening.isEmpty()) return result
        val listeningPlaceholders = usersAllowedToShareListening.joinToString(",") { "?" }
        jdbc.query(
            """
            select event.user_id,event.canonical_key,event.title,event.artist_name,event.completion_ratio,
              extract(epoch from (now()-event.ended_at))/86400.0 age_days
            from music_listen_events event
            where event.user_id in ($listeningPlaceholders)
              and event.ended_at>now()-interval '90 days'
            order by event.ended_at desc
            """.trimIndent(),
            { rs ->
                val userId = UUID.fromString(rs.getString("user_id"))
                val completion = rs.getDouble("completion_ratio").takeUnless { rs.wasNull() } ?: 0.5
                val ageDays = rs.getDouble("age_days").coerceAtLeast(0.0)
                val recency = 1.0 / (1.0 + ageDays / 30.0)
                val weight = (0.35 + completion.coerceIn(0.0, 1.0) * 0.65) * recency
                result.getValue(userId).add(
                    TasteSignal(
                        rs.getString("canonical_key").ifBlank {
                            "${rs.getString("title")} · ${rs.getString("artist_name")}"
                        },
                        "LISTEN",
                        weight,
                    )
                )
                result.getValue(userId).add(
                    TasteSignal(rs.getString("artist_name"), "ARTIST", weight * 0.35)
                )
                result.getValue(userId).add(
                    TasteSignal(
                        "${rs.getString("title")} · ${rs.getString("artist_name")}",
                        "TRACK",
                        weight * 0.5,
                    )
                )
            },
            *usersAllowedToShareListening.toTypedArray(),
        )
        return result
    }

    private fun logExposures(
        viewerUserId: UUID,
        matches: Map<UUID, TasteMatchSummary>,
        context: String,
    ) {
        matches.forEach { (targetUserId, match) ->
            jdbc.update(
                """
                insert into taste_match_exposures(
                  viewer_user_id,target_user_id,context,score,confidence,algorithm_version
                )
                select ?,?,?,?,?,?
                where not exists(
                  select 1 from taste_match_exposures
                  where viewer_user_id=? and target_user_id=? and context=?
                    and algorithm_version=? and created_at>now()-interval '5 minutes'
                )
                """.trimIndent(),
                viewerUserId,
                targetUserId,
                context,
                match.score,
                match.confidence,
                match.algorithmVersion,
                viewerUserId,
                targetUserId,
                context,
                match.algorithmVersion,
            )
        }
    }

    private fun splitTags(value: String?) = value.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)

    private companion object {
        val EXPOSURE_CONTEXTS = setOf("NEARBY", "PROFILE", "LOUNGE")
        const val MATCH_CACHE_TTL_NANOS = 5_000_000_000L
    }
}

private data class MatchCacheKey(val viewerUserId: UUID, val targetUserId: UUID)
private data class CachedMatch(val summary: TasteMatchSummary, val expiresAtNanos: Long)

internal object TasteEmbeddingAlgorithm {
    const val ALGORITHM_VERSION = "HYBRID_SIAMESE_V2_BOOTSTRAP"
    private const val DIMENSIONS = 128
    private const val MINIMUM_EVIDENCE = 3

    fun encode(input: List<TasteSignal>): TasteVector {
        val merged = linkedMapOf<String, TasteSignal>()
        input.forEach { signal ->
            val label = signal.label.trim().take(160)
            if (label.isBlank() || signal.weight <= 0.0) return@forEach
            val key = "${signal.type.uppercase()}:${label.lowercase()}"
            val previous = merged[key]
            merged[key] = signal.copy(
                label = label,
                type = signal.type.uppercase(),
                // Repeated listening remains meaningful without allowing one looped song to
                // overwhelm the user's broader explicit and historical taste evidence.
                weight = ((previous?.weight ?: 0.0) + signal.weight).coerceAtMost(6.0),
            )
        }
        val vector = DoubleArray(DIMENSIONS)
        merged.forEach { (key, signal) ->
            val index = stableHash(key).mod(DIMENSIONS)
            val sign = if (stableHash("sign:$key") and 1 == 0) 1.0 else -1.0
            vector[index] += signal.weight * sign
        }
        val norm = sqrt(vector.sumOf { it * it })
        if (norm > 0.0) vector.indices.forEach { vector[it] /= norm }
        return TasteVector(vector, merged)
    }

    fun compare(left: TasteVector, right: TasteVector): TasteMatchSummary {
        val sampleSize = left.evidenceCount + right.evidenceCount
        val minimumEvidence = minOf(left.evidenceCount, right.evidenceCount)
        val confidence = confidenceForEvidence(minimumEvidence)
        val reasons = overlapMetrics(left.signals, right.signals)
        if (minimumEvidence < MINIMUM_EVIDENCE) {
            return TasteMatchSummary(
                score = null,
                confidence = confidence,
                metrics = reasons,
                sampleSize = sampleSize,
            )
        }

        val cosine = left.values.indices.sumOf { left.values[it] * right.values[it] }.coerceIn(-1.0, 1.0)
        val rawScore = (cosine + 1.0) * 50.0
        val reliability = when (confidence) {
            "HIGH" -> 1.0
            "MEDIUM" -> 0.75
            else -> 0.45
        }
        val calibrated = 50.0 + reliability * (rawScore - 50.0)
        return TasteMatchSummary(
            score = calibrated.toInt().coerceIn(0, 100),
            confidence = confidence,
            metrics = reasons,
            sampleSize = sampleSize,
        )
    }

    private fun overlapMetrics(
        left: Map<String, TasteSignal>,
        right: Map<String, TasteSignal>,
    ): List<TasteMatchMetric> = left.keys.intersect(right.keys).mapNotNull { key ->
        val a = left[key] ?: return@mapNotNull null
        val b = right[key] ?: return@mapNotNull null
        val largest = maxOf(a.weight, b.weight)
        if (largest <= 0.0) return@mapNotNull null
        TasteMatchMetric(
            label = a.label,
            type = a.type,
            score = ((minOf(a.weight, b.weight) / largest) * 100.0).toInt().coerceIn(0, 100),
            evidenceCount = minOf(a.weight, b.weight).toInt().coerceAtLeast(1),
        )
    }.sortedWith(
        compareByDescending<TasteMatchMetric> { it.score }
            .thenByDescending { it.evidenceCount }
            .thenBy { it.type }
            .thenBy { it.label.lowercase() },
    ).take(4)

    fun confidenceForEvidence(evidence: Int): String = when {
        evidence >= 30 -> "HIGH"
        evidence >= 10 -> "MEDIUM"
        else -> "LOW"
    }

    private fun stableHash(value: String): Int {
        var hash = 0x811c9dc5.toInt()
        value.forEach { character ->
            hash = hash xor character.code
            hash *= 0x01000193
        }
        return hash and Int.MAX_VALUE
    }
}

@Service
class TasteEmbeddingRefreshWorker(private val tasteMatches: TasteMatchService) {
    @Scheduled(fixedDelayString = "\${app.taste.embedding-refresh-millis:10000}")
    fun refresh() = tasteMatches.refreshDirtyBatch()
}
