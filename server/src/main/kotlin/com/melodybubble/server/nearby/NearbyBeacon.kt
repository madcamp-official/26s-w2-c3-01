package com.melodybubble.server.nearby

import com.melodybubble.server.safety.ActionRateLimiter
import java.security.Principal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class NearbyBeaconRequest(val clientSessionId: String)
data class NearbyBeaconResponse(
    val beaconId: String,
    val expiresAt: Instant,
    val rotationAfterSeconds: Int = 30,
)
data class ResolveNearbyBeaconsRequest(val beaconIds: List<String>)
data class ResolvedNearbyBeacon(val beaconId: String, val user: NearbyBubble)
data class DirectProximityUpdate(
    val beaconId: String,
    val proximity: String,
    val confidence: String,
    val method: String,
    val sequence: Long,
    val observedAtEpochMillis: Long,
)
data class DirectProximityBatchRequest(val updates: List<DirectProximityUpdate> = emptyList())
data class DirectProximityBatchResponse(val acceptedCount: Int, val receivedCount: Int)

internal fun normalizeDirectProximityBatch(
    updates: List<DirectProximityUpdate>,
    maxSize: Int = 40,
): List<DirectProximityUpdate> = updates
    .groupBy(DirectProximityUpdate::beaconId)
    .mapNotNull { (_, sameBeaconUpdates) ->
        sameBeaconUpdates.maxWithOrNull(
            compareBy<DirectProximityUpdate>(DirectProximityUpdate::observedAtEpochMillis)
                .thenBy(DirectProximityUpdate::sequence),
        )
    }
    .sortedWith(
        compareByDescending<DirectProximityUpdate>(DirectProximityUpdate::observedAtEpochMillis)
            .thenByDescending(DirectProximityUpdate::sequence),
    )
    .take(maxSize)
    .sortedWith(
        compareBy<DirectProximityUpdate>(DirectProximityUpdate::observedAtEpochMillis)
            .thenBy(DirectProximityUpdate::sequence),
    )

@Service
class NearbyBeaconService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
    private val nearby: NearbyService,
) {
    @Transactional
    fun issue(userId: UUID, request: NearbyBeaconRequest): NearbyBeaconResponse {
        rateLimiter.enforce(userId, "NEARBY_BEACON_ISSUE", 10, Duration.ofMinutes(1))
        val sessionId = request.clientSessionId.take(128)
        require(sessionId.isNotBlank()) { "clientSessionId is required" }
        val beaconId = "mb1_" + UUID.randomUUID().toString().replace("-", "")
        val expiresAt = Instant.now().plusSeconds(75)
        jdbc.update(
            "delete from nearby_beacons where user_id=? and expires_at<now()",
            userId,
        )
        jdbc.update(
            "insert into nearby_beacons(beacon_id,user_id,client_session_id,expires_at) values (?,?,?,?)",
            beaconId, userId, sessionId, Timestamp.from(expiresAt),
        )
        return NearbyBeaconResponse(beaconId, expiresAt)
    }

    fun resolve(viewerId: UUID, request: ResolveNearbyBeaconsRequest): List<ResolvedNearbyBeacon> {
        rateLimiter.enforce(viewerId, "NEARBY_BEACON_RESOLVE", 60, Duration.ofMinutes(1))
        val ids = request.beaconIds.distinct().filter { it.matches(Regex("mb1_[a-f0-9]{32}")) }.take(40)
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val owners = jdbc.query(
            "select beacon_id,user_id from nearby_beacons where beacon_id in ($placeholders) and expires_at>now() and user_id<>?",
            { rs, _ -> rs.getString("beacon_id") to UUID.fromString(rs.getString("user_id")) },
            *(ids.map<String, Any> { it } + viewerId).toTypedArray(),
        )
        return owners.mapNotNull { (beaconId, ownerId) ->
            nearby.directBubble(viewerId, ownerId)?.let { ResolvedNearbyBeacon(beaconId, it) }
        }
    }

    @Transactional
    fun reportProximity(viewerId: UUID, update: DirectProximityUpdate): Boolean {
        rateLimiter.enforce(viewerId, "NEARBY_DIRECT_PROXIMITY", 600, Duration.ofMinutes(1))
        return reportProximityUnchecked(viewerId, update)
    }

    /**
     * Reports every currently observed peer in one request. The client sends at most one batch per
     * second, so the request budget no longer scales with the number of nearby phones or with
     * overlapping old/new beacon IDs during rotation.
     */
    @Transactional
    fun reportProximityBatch(
        viewerId: UUID,
        request: DirectProximityBatchRequest,
    ): DirectProximityBatchResponse {
        rateLimiter.enforce(viewerId, "NEARBY_DIRECT_BATCH", 75, Duration.ofMinutes(1))
        val updates = normalizeDirectProximityBatch(
            request.updates,
            MAX_DIRECT_PROXIMITY_BATCH_SIZE,
        )
        val acceptedCount = updates.count { reportProximityUnchecked(viewerId, it) }
        return DirectProximityBatchResponse(
            acceptedCount = acceptedCount,
            receivedCount = updates.size,
        )
    }

    private fun reportProximityUnchecked(viewerId: UUID, update: DirectProximityUpdate): Boolean {
        val proximity = proximityBandFromWire(update.proximity) ?: return false
        if (!update.beaconId.matches(Regex("mb1_[a-f0-9]{32}")) ||
            update.confidence !in DistanceConfidence.entries.map { it.name } ||
            update.method !in setOf("UWB", "WIFI_RTT", "BLUETOOTH") ||
            update.sequence <= 0L
        ) return false
        val targetId = jdbc.query(
            "select user_id from nearby_beacons where beacon_id=? and expires_at>now() and user_id<>?",
            { rs, _ -> UUID.fromString(rs.getString("user_id")) },
            update.beaconId,
            viewerId,
        ).firstOrNull() ?: return false
        val now = Instant.now()
        val observedAtMillis = update.observedAtEpochMillis.takeIf {
            it in (now.toEpochMilli() - 10_000L)..(now.toEpochMilli() + 5_000L)
        } ?: now.toEpochMilli()
        return jdbc.update(
            """
            INSERT INTO direct_proximity_measurements(
              viewer_user_id,target_user_id,proximity,confidence,method,sequence,observed_at,expires_at
            ) VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(viewer_user_id,target_user_id) DO UPDATE SET
              proximity=excluded.proximity,confidence=excluded.confidence,method=excluded.method,
              sequence=excluded.sequence,observed_at=excluded.observed_at,expires_at=excluded.expires_at
            WHERE direct_proximity_measurements.sequence < excluded.sequence
            """.trimIndent(),
            viewerId, targetId, proximity.name, update.confidence, update.method, update.sequence,
            Timestamp.from(Instant.ofEpochMilli(observedAtMillis)),
            Timestamp.from(now.plusSeconds(DIRECT_PROXIMITY_TTL_SECONDS)),
        ) > 0
    }

    private companion object {
        // A scan callback can pause for a few seconds even while two phones remain adjacent.
        // The client refreshes at most once per second, so this bridges transient radio gaps.
        const val DIRECT_PROXIMITY_TTL_SECONDS = 12L
        const val MAX_DIRECT_PROXIMITY_BATCH_SIZE = 40
    }
}

@RestController
@RequestMapping("/api/v1/nearby/beacons")
class NearbyBeaconController(private val service: NearbyBeaconService) {
    @PostMapping
    fun issue(principal: Principal, @RequestBody request: NearbyBeaconRequest) =
        service.issue(UUID.fromString(principal.name), request)

    @PostMapping("/resolve")
    fun resolve(principal: Principal, @RequestBody request: ResolveNearbyBeaconsRequest) =
        service.resolve(UUID.fromString(principal.name), request)

    @PostMapping("/proximity")
    fun reportProximity(principal: Principal, @RequestBody update: DirectProximityUpdate) =
        service.reportProximity(UUID.fromString(principal.name), update)

    @PostMapping("/proximity/batch")
    fun reportProximityBatch(principal: Principal, @RequestBody request: DirectProximityBatchRequest) =
        service.reportProximityBatch(UUID.fromString(principal.name), request)
}
