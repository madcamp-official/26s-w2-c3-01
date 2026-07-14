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
            "delete from nearby_beacons where user_id=? and (client_session_id=? or expires_at<now())",
            userId, sessionId,
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
}
