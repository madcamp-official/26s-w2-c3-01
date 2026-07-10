package com.melodybubble.server.nearby

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Instant
import java.util.UUID

data class TrackSummary(val title: String, val artist: String, val albumArtUrl: String? = null)
data class AbstractPosition(val x: Float, val y: Float)
data class NearbyBubble(val nearbyHandle: String, val displayAlias: String, val profileColor: String, val displayPosition: AbstractPosition, val matchScore: Int, val proximity: String, val track: TrackSummary?)
data class NearbySnapshot(val generatedAt: Instant = Instant.now(), val items: List<NearbyBubble>)
data class LocationUpdate(val requestId: String, val clientSessionId: String, val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null)
data class Envelope<T>(val type: String, val data: T, val emittedAt: Instant = Instant.now())

@Service
class NearbyService(private val jdbc: JdbcTemplate, @Value("\${app.nearby.radius-meters}") private val radius: Int) {
    fun snapshot(userId: UUID): NearbySnapshot {
        val sql = """
          SELECT ps.nearby_handle, u.display_name, u.profile_color,
                 ms.track_title, ms.artist_name,
                 ST_DistanceSphere(me.point, other.point) AS distance_meters
          FROM current_locations me
          JOIN presence_sessions mine ON mine.id=me.session_id AND mine.user_id=? AND mine.expires_at>now()
          JOIN current_locations other ON ST_DWithin(me.point::geography, other.point::geography, ?) AND other.expires_at>now()
          JOIN presence_sessions ps ON ps.id=other.session_id AND ps.expires_at>now() AND ps.user_id<>?
          JOIN users u ON u.id=ps.user_id
          LEFT JOIN music_statuses ms ON ms.user_id=u.id AND ms.expires_at>now()
          ORDER BY distance_meters LIMIT 40
        """.trimIndent()
        return NearbySnapshot(items = jdbc.query(sql, { rs, _ ->
            val handle = rs.getString("nearby_handle")
            NearbyBubble(handle, rs.getString("display_name"), rs.getString("profile_color"), position(handle), 65 + (stable(handle, 31)), proximity(rs.getDouble("distance_meters")),
                rs.getString("track_title")?.let { TrackSummary(it, rs.getString("artist_name")) })
        }, userId, radius, userId))
    }

    fun updateLocation(userId: UUID, update: LocationUpdate) {
        val expires = "now() + interval '90 seconds'"
        val session = jdbc.query("""INSERT INTO presence_sessions(id,user_id,client_session_id,nearby_handle,expires_at)
            VALUES (gen_random_uuid(), ?, ?, concat('n_', replace(gen_random_uuid()::text,'-','')), $expires)
            ON CONFLICT(user_id,client_session_id) DO UPDATE SET expires_at=$expires,last_seen_at=now()
            RETURNING id""", { rs, _ -> UUID.fromString(rs.getString(1)) }, userId, update.clientSessionId).first()
        jdbc.update("""INSERT INTO current_locations(session_id,point,accuracy_meters,expires_at) VALUES (?, ST_SetSRID(ST_MakePoint(?,?),4326), ?, $expires)
            ON CONFLICT(session_id) DO UPDATE SET point=excluded.point,accuracy_meters=excluded.accuracy_meters,expires_at=excluded.expires_at,updated_at=now()""",
            session, update.longitude, update.latitude, update.accuracyMeters)
    }
    private fun proximity(distance: Double) = when { distance < 45 -> "VERY_CLOSE"; distance < 130 -> "CLOSE"; else -> "AROUND" }
    private fun stable(value: String, modulo: Int) = (value.hashCode().toUInt().toLong() % modulo).toInt()
    private fun position(handle: String) = AbstractPosition((stable(handle, 161) - 80) / 100f, (stable(handle.reversed(), 161) - 80) / 100f)
}

@RestController
@RequestMapping("/api/v1/nearby")
class NearbyController(private val nearby: NearbyService) {
    @GetMapping("/snapshot") fun snapshot(principal: Principal) = nearby.snapshot(UUID.fromString(principal.name))
    @GetMapping("/{handle}") fun detail(principal: Principal, @PathVariable handle: String): NearbyBubble =
        nearby.snapshot(UUID.fromString(principal.name)).items.firstOrNull { it.nearbyHandle == handle }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Nearby user is no longer available")
}

@org.springframework.messaging.handler.annotation.MessageMapping("location/update")
@org.springframework.stereotype.Controller
class PresenceMessageController(private val nearby: NearbyService, private val messaging: SimpMessagingTemplate) {
    @org.springframework.messaging.simp.annotation.SendToUser("/queue/ack")
    fun updateLocation(update: LocationUpdate, principal: Principal): Envelope<Map<String, String>> {
        val userId = UUID.fromString(principal.name)
        nearby.updateLocation(userId, update)
        messaging.convertAndSendToUser(userId.toString(), "/queue/nearby", Envelope("NEARBY_SNAPSHOT", nearby.snapshot(userId)))
        return Envelope("ACK", mapOf("requestId" to update.requestId))
    }
}
