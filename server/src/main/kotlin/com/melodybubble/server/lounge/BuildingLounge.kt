package com.melodybubble.server.lounge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.melodybubble.server.realtime.RealtimeEnvelope
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.Duration
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class BuildingLoungeSummary(
    val id: UUID,
    val buildingId: UUID,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val category: String,
    val distanceMeters: Double,
    val inside: Boolean,
    val activeMembers: Int,
    val subLoungeCount: Int,
)

data class EnterBuildingLoungeRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val wifiFingerprint: String,
)
data class BuildingLoungeSessionResponse(val lounge: BuildingLoungeSummary, val entered: Boolean)
data class HeartbeatRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val wifiFingerprint: String,
)
data class HeartbeatResponse(val inside: Boolean, val outsideCount: Int, val forcedExit: Boolean)
data class CreateSubLoungeRequest(val title: String, val style: String? = null)
data class SubLoungeSummary(
    val id: UUID,
    val buildingLoungeId: UUID,
    val title: String,
    val style: String?,
    val memberCount: Int,
    val createdAt: Instant,
)

data class ListeningStatusRequest(
    val trackTitle: String,
    val artistName: String,
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = true,
)

data class RecommendationCardRequest(val trackTitle: String, val artistName: String, val message: String? = null)
data class RecommendationCardSummary(
    val id: UUID,
    val subLoungeId: UUID,
    val senderId: UUID,
    val trackTitle: String,
    val artistName: String,
    val message: String?,
    val reactionCount: Int,
    val createdAt: Instant,
)

data class ReactionRequest(val reactionType: String = "LIKE")

@Service
class BuildingLoungeService(
    private val jdbc: JdbcTemplate,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    private val rateLimiter: ActionRateLimiter,
    private val subLoungeRealtime: SubLoungeRealtimeService,
) {
    private val overpassClient = restClientBuilder
        .baseUrl("https://overpass-api.de/api")
        .build()

    @Transactional
    fun nearby(
        userId: UUID,
        latitude: Double,
        longitude: Double,
        wifiFingerprint: String,
        wifiName: String,
    ): List<BuildingLoungeSummary> {
        validateCoordinate(latitude, longitude)
        validateWifiFingerprint(wifiFingerprint)
        rateLimiter.enforce(userId, "LOUNGE_DISCOVERY", 60, Duration.ofMinutes(10))
        refreshCandidate(userId, wifiFingerprint, wifiName, latitude, longitude)
        reconcileWifiLounges(wifiFingerprint, wifiName)
        return jdbc.query(
            """
            SELECT bl.id, b.id AS building_id, b.name, b.address,
                   ST_Y(b.point) AS latitude, ST_X(b.point) AS longitude,
                   b.radius_m, b.category,
                   ST_DistanceSphere(b.point, ST_SetSRID(ST_MakePoint(?, ?), 4326)) AS distance_meters,
                   ST_DWithin(b.point::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, b.radius_m) AS inside,
                   (SELECT count(DISTINCT candidate.user_id)
                    FROM wifi_lounge_candidates candidate
                    WHERE candidate.wifi_fingerprint=? AND candidate.expires_at>now()
                      AND ST_DWithin(candidate.point::geography,b.point::geography,b.radius_m)
                   ) AS active_members,
                   count(DISTINCT sl.id) FILTER (WHERE sl.active = true) AS sub_lounge_count
            FROM lounge_buildings b
            JOIN building_lounges bl ON bl.building_id = b.id AND bl.active = true
            LEFT JOIN sub_lounges sl ON sl.building_lounge_id = bl.id
            WHERE b.active=true AND b.category='WIFI' AND b.google_place_id LIKE ?
              AND ST_DWithin(
                b.point::geography,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?
              )
            GROUP BY bl.id, b.id
            """.trimIndent(),
            { rs, _ -> rs.toBuildingLoungeSummary() },
            longitude,
            latitude,
            longitude,
            latitude,
            wifiFingerprint,
            "wifi-$wifiFingerprint-%",
            longitude,
            latitude,
            WIFI_DISCOVERY_METERS,
        )
    }

    private fun refreshCandidate(
        userId: UUID,
        wifiFingerprint: String,
        wifiName: String,
        latitude: Double,
        longitude: Double,
    ) {
        jdbc.update(
            """
            INSERT INTO wifi_lounge_candidates(user_id,wifi_fingerprint,wifi_name,point,expires_at)
            VALUES (?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),now() + interval '90 seconds')
            ON CONFLICT(user_id) DO UPDATE SET
              wifi_fingerprint=excluded.wifi_fingerprint,wifi_name=excluded.wifi_name,
              point=excluded.point,expires_at=excluded.expires_at,updated_at=now()
            """.trimIndent(),
            userId, wifiFingerprint, wifiName.trim().take(80).ifBlank { "Wi-Fi" }, longitude, latitude,
        )
    }

    private fun reconcileWifiLounges(wifiFingerprint: String, wifiName: String) {
        val normalizedName = wifiName.trim().take(80).ifBlank { "Wi-Fi" }
        val buildingName = "$normalizedName 라운지"
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", { _, _ -> Unit }, wifiFingerprint)
        val candidates = jdbc.query(
            """
            SELECT candidate.user_id,ST_Y(active_location.point) latitude,ST_X(active_location.point) longitude
            FROM wifi_lounge_candidates candidate
            JOIN user_privacy_settings privacy ON privacy.user_id=candidate.user_id
              AND privacy.discoverable=true AND privacy.discoverability_scope<>'HIDDEN'
            JOIN LATERAL (
              SELECT location.point
              FROM presence_sessions session
              JOIN current_locations location ON location.session_id=session.id
              WHERE session.user_id=candidate.user_id AND session.expires_at>now() AND location.expires_at>now()
              ORDER BY session.last_seen_at DESC LIMIT 1
            ) active_location ON true
            WHERE candidate.wifi_fingerprint=? AND candidate.expires_at>now()
            """.trimIndent(),
            { rs, _ -> LoungeCandidate(rs.getString("user_id"), rs.getDouble("latitude"), rs.getDouble("longitude")) },
            wifiFingerprint,
        )
        val circles = LoungeGeometry.circles(candidates)
        val existing = jdbc.query(
            """
            SELECT lounge.id,building.id building_id,ST_Y(building.point) latitude,ST_X(building.point) longitude
            FROM lounge_buildings building JOIN building_lounges lounge ON lounge.building_id=building.id
            WHERE building.category='WIFI' AND building.google_place_id LIKE ?
            """.trimIndent(),
            { rs, _ -> ExistingWifiLounge(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("building_id")), rs.getDouble("latitude"), rs.getDouble("longitude")) },
            "wifi-$wifiFingerprint-%",
        ).toMutableList()
        val activated = mutableListOf<UUID>()
        circles.forEach { circle ->
            val match = existing.minByOrNull { LoungeGeometry.distanceMeters(circle.latitude, circle.longitude, it.latitude, it.longitude) }
            if (match != null) existing.remove(match)
            val loungeId = if (match == null) createWifiLounge(wifiFingerprint, buildingName, circle) else {
                jdbc.update(
                    "UPDATE lounge_buildings SET name=?,address=?,point=ST_SetSRID(ST_MakePoint(?,?),4326),radius_m=?,active=true,updated_at=now() WHERE id=?",
                    buildingName, "같은 Wi-Fi를 사용하는 주변 사용자 라운지", circle.longitude, circle.latitude,
                    circle.radiusMeters.coerceAtMost(2000), match.buildingId,
                )
                jdbc.update("UPDATE building_lounges SET title=?,active=true WHERE id=?", buildingName, match.loungeId)
                match.loungeId
            }
            activated += loungeId
        }
        if (existing.isNotEmpty()) {
            val placeholders = existing.joinToString(",") { "?" }
            val sourceIds = existing.map(ExistingWifiLounge::loungeId)
            jdbc.update("UPDATE building_lounges SET active=false WHERE id IN ($placeholders)", *sourceIds.toTypedArray())
            activated.firstOrNull()?.let { consolidateSubLounges(it, sourceIds) }
        }
    }

    private fun createWifiLounge(fingerprint: String, name: String, circle: LoungeCircle): UUID {
        val buildingId = jdbc.query(
            """INSERT INTO lounge_buildings(name,address,google_place_id,point,radius_m,category,active)
               VALUES (?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?,'WIFI',true) RETURNING id""",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            name, "같은 Wi-Fi를 사용하는 주변 사용자 라운지", "wifi-$fingerprint-${UUID.randomUUID()}",
            circle.longitude, circle.latitude, circle.radiusMeters.coerceAtMost(2000),
        ).first()
        return jdbc.query(
            "INSERT INTO building_lounges(building_id,title,active) VALUES (?,?,true) RETURNING id",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, buildingId, name,
        ).first()
    }

    private fun consolidateSubLounges(target: UUID, sources: List<UUID>) {
        if (sources.isEmpty()) return
        val placeholders = sources.joinToString(",") { "?" }
        jdbc.update(
            "UPDATE building_lounge_sessions SET active=false,expires_at=now() WHERE building_lounge_id IN ($placeholders)",
            *sources.toTypedArray(),
        )
        val capacity = (MAX_SUB_LOUNGES - activeSubLoungeCount(target)).coerceAtLeast(0)
        val moving = jdbc.query(
            "SELECT id FROM sub_lounges WHERE active=true AND building_lounge_id IN ($placeholders) ORDER BY created_at LIMIT $capacity",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, *sources.toTypedArray(),
        )
        moving.forEach { jdbc.update("UPDATE sub_lounges SET building_lounge_id=? WHERE id=?", target, it) }
        jdbc.update("UPDATE sub_lounges SET active=false WHERE active=true AND building_lounge_id IN ($placeholders)", *sources.toTypedArray())
    }

    private data class ExistingWifiLounge(
        val loungeId: UUID,
        val buildingId: UUID,
        val latitude: Double,
        val longitude: Double,
    )

    private fun hasFreshBuildingCache(latitude: Double, longitude: Double): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM lounge_buildings
              WHERE active=true AND google_place_id LIKE 'osm-%'
                AND updated_at > now() - interval '24 hours'
                AND ST_DWithin(
                  point::geography,
                  ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                  1600
                )
            )
            """.trimIndent(),
            Boolean::class.java,
            longitude,
            latitude,
        ) == true

    @Transactional
    fun enter(userId: UUID, loungeId: UUID, request: EnterBuildingLoungeRequest): BuildingLoungeSessionResponse {
        validateWifiFingerprint(request.wifiFingerprint)
        requireCompatibleWifi(loungeId, request.wifiFingerprint)
        val lounge = requireInside(loungeId, request.latitude, request.longitude)
        jdbc.update(
            """
            INSERT INTO building_lounge_sessions(
              user_id, building_lounge_id, last_point, accuracy_meters, wifi_fingerprint, outside_count, active, expires_at
            )
            VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, 0, true, now() + interval '90 seconds')
            ON CONFLICT(user_id, building_lounge_id) DO UPDATE SET
              last_point = excluded.last_point,
              accuracy_meters = excluded.accuracy_meters,
              wifi_fingerprint = excluded.wifi_fingerprint,
              outside_count = 0,
              active = true,
              last_seen_at = now(),
              expires_at = now() + interval '90 seconds'
            """.trimIndent(),
            userId,
            loungeId,
            request.longitude,
            request.latitude,
            request.accuracyMeters,
            request.wifiFingerprint,
        )
        return BuildingLoungeSessionResponse(lounge = lounge, entered = true)
    }

    private fun cacheRealBuildings(latitude: Double, longitude: Double) {
        validateCoordinate(latitude, longitude)
        discoverRealBuildings(latitude, longitude).forEach { building ->
            val buildingId = jdbc.query(
                """
                INSERT INTO lounge_buildings(name, address, google_place_id, point, radius_m, category, active)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, true)
                ON CONFLICT(google_place_id) DO UPDATE SET
                  name = excluded.name,
                  address = excluded.address,
                  point = excluded.point,
                  radius_m = excluded.radius_m,
                  category = excluded.category,
                  active = true,
                  updated_at = now()
                RETURNING id
                """.trimIndent(),
                { rs, _ -> UUID.fromString(rs.getString("id")) },
                building.name,
                building.address,
                building.placeId,
                building.longitude,
                building.latitude,
                building.radiusMeters,
                building.category,
            ).first()
            val loungeId = jdbc.query(
                """
                INSERT INTO building_lounges(building_id, title, active)
                VALUES (?, ?, true)
                ON CONFLICT(building_id) DO UPDATE SET title = excluded.title, active = true
                RETURNING id
                """.trimIndent(),
                { rs, _ -> UUID.fromString(rs.getString("id")) },
                buildingId,
                building.name,
            ).first()
        }
    }

    private fun discoverRealBuildings(latitude: Double, longitude: Double): List<DiscoveredBuilding> = runCatching {
        val query = """
            [out:json][timeout:8];
            (
              way["building"](around:900,$latitude,$longitude);
              relation["building"](around:900,$latitude,$longitude);
            );
            out center tags 30;
        """.trimIndent()
        val body = overpassClient.post()
            .uri("/interpreter")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("data=${URLEncoder.encode(query, StandardCharsets.UTF_8)}")
            .retrieve()
            .body(String::class.java)
            .orEmpty()
        val root = objectMapper.readTree(body)
        root.path("elements")
            .filter { it.has("center") }
            .mapNotNull { it.toDiscoveredBuilding(latitude, longitude) }
            .sortedBy { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .distinctBy { it.placeId }
            .take(4)
    }.getOrDefault(emptyList())

    private fun JsonNode.toDiscoveredBuilding(userLatitude: Double, userLongitude: Double): DiscoveredBuilding? {
        val center = path("center")
        val lat = center.path("lat").asDouble(Double.NaN)
        val lon = center.path("lon").asDouble(Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val tags = path("tags")
        val rawName = listOf("name:ko", "name", "addr:housename")
            .firstNotNullOfOrNull { key -> tags.path(key).asText(null)?.takeIf { it.isNotBlank() } }
        val address = buildAddress(tags)
        val id = path("id").asText()
        val osmType = path("type").asText("building")
        val buildingType = tags.path("building").asText("building")
        val category = when {
            tags.path("shop").asText("").isNotBlank() -> "SHOPPING"
            buildingType.contains("commercial", ignoreCase = true) -> "COMMERCIAL"
            buildingType.contains("retail", ignoreCase = true) -> "SHOPPING"
            buildingType.contains("university", ignoreCase = true) || buildingType.contains("school", ignoreCase = true) -> "CAMPUS"
            buildingType.contains("apartments", ignoreCase = true) || buildingType.contains("residential", ignoreCase = true) -> "RESIDENTIAL"
            else -> "BUILDING"
        }
        val distance = distanceMeters(userLatitude, userLongitude, lat, lon)
        val radius = when (category) {
            "SHOPPING" -> 260
            "COMMERCIAL" -> 190
            "CAMPUS" -> 320
            "RESIDENTIAL" -> 120
            else -> 160
        } + if (distance < 80) 80 else 0
        return DiscoveredBuilding(
            placeId = "osm-$osmType-$id",
            name = rawName ?: address.takeUnless { it == "주소 정보 없음" } ?: "이름 없는 건물",
            address = address,
            latitude = lat,
            longitude = lon,
            radiusMeters = radius.coerceIn(90, 420),
            category = category,
        )
    }

    fun heartbeat(userId: UUID, loungeId: UUID, request: HeartbeatRequest): HeartbeatResponse {
        validateWifiFingerprint(request.wifiFingerprint)
        if (!sessionUsesWifi(userId, loungeId, request.wifiFingerprint)) {
            leave(userId, loungeId)
            return HeartbeatResponse(inside = false, outsideCount = 3, forcedExit = true)
        }
        val lounge = loungeAt(loungeId, request.latitude, request.longitude)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Building lounge not found")
        val nextOutsideCount = if (lounge.inside) 0 else currentOutsideCount(userId, loungeId) + 1
        val forcedExit = nextOutsideCount >= 3
        jdbc.update(
            """
            UPDATE building_lounge_sessions
            SET last_point = ST_SetSRID(ST_MakePoint(?, ?), 4326),
                accuracy_meters = ?,
                outside_count = ?,
                active = ?,
                last_seen_at = now(),
                expires_at = now() + interval '90 seconds'
            WHERE user_id = ? AND building_lounge_id = ?
            """.trimIndent(),
            request.longitude,
            request.latitude,
            request.accuracyMeters,
            nextOutsideCount,
            !forcedExit,
            userId,
            loungeId,
        )
        if (forcedExit) {
            subLoungeRealtime.forceLeaveFromBuilding(userId, loungeId)
        }
        return HeartbeatResponse(inside = lounge.inside, outsideCount = nextOutsideCount, forcedExit = forcedExit)
    }

    fun leave(userId: UUID, loungeId: UUID) {
        jdbc.update(
            "UPDATE building_lounge_sessions SET active=false, expires_at=now(), last_seen_at=now() WHERE user_id=? AND building_lounge_id=?",
            userId,
            loungeId,
        )
        subLoungeRealtime.forceLeaveFromBuilding(userId, loungeId)
    }

    fun subLounges(loungeId: UUID): List<SubLoungeSummary> = jdbc.query(
        """
        SELECT sl.id, sl.building_lounge_id, sl.title, sl.style, sl.created_at,
               count(m.user_id) FILTER (
                 WHERE m.active = true AND session.active = true AND session.expires_at > now()
               ) AS member_count
        FROM sub_lounges sl
        LEFT JOIN sub_lounge_members m ON m.sub_lounge_id = sl.id
        LEFT JOIN building_lounge_sessions session
          ON session.user_id=m.user_id AND session.building_lounge_id=sl.building_lounge_id
        WHERE sl.building_lounge_id = ? AND sl.active = true
        GROUP BY sl.id
        ORDER BY sl.created_at DESC
        """.trimIndent(),
        { rs, _ ->
            SubLoungeSummary(
                id = UUID.fromString(rs.getString("id")),
                buildingLoungeId = UUID.fromString(rs.getString("building_lounge_id")),
                title = rs.getString("title"),
                style = rs.getString("style"),
                memberCount = rs.getInt("member_count"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        },
        loungeId,
    )

    @Transactional
    fun createSubLounge(userId: UUID, loungeId: UUID, request: CreateSubLoungeRequest): SubLoungeSummary {
        rateLimiter.enforce(userId, "LOUNGE_CREATE", 5, Duration.ofHours(1))
        requireActiveBuildingSession(userId, loungeId)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", { _, _ -> Unit }, "sub-lounges:$loungeId")
        if (activeSubLoungeCount(loungeId) >= MAX_SUB_LOUNGES) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "사운드룸은 최대 5개까지 만들 수 있습니다")
        }
        val title = request.title.trim().take(80)
        if (title.length < 2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "사운드룸 이름은 2자 이상이어야 합니다.")
        }
        val style = request.style?.trim()?.take(80)?.ifBlank { null }
        val duplicate = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM sub_lounges WHERE building_lounge_id=? AND active=true AND lower(title)=lower(?))",
            Boolean::class.java,
            loungeId,
            title,
        ) == true
        if (duplicate) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "같은 이름의 사운드룸이 이미 있어요.")
        }
        val id = jdbc.query(
            "INSERT INTO sub_lounges(building_lounge_id, creator_user_id, title, style) VALUES (?, ?, ?, ?) RETURNING id",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            loungeId,
            userId,
            title,
            style,
        ).first()
        joinSubLounge(userId, id)
        return subLounges(loungeId).first { it.id == id }
    }

    fun joinSubLounge(userId: UUID, subLoungeId: UUID): SubLoungeSummary {
        val buildingLoungeId = buildingLoungeIdForSubLounge(subLoungeId)
        requireActiveBuildingSession(userId, buildingLoungeId)
        jdbc.update(
            """
            INSERT INTO sub_lounge_members(sub_lounge_id, user_id, active)
            VALUES (?, ?, true)
            ON CONFLICT(sub_lounge_id, user_id) DO UPDATE SET active=true, last_seen_at=now()
            """.trimIndent(),
            subLoungeId,
            userId,
        )
        return subLounges(buildingLoungeId).first { it.id == subLoungeId }
    }

    fun updateListening(userId: UUID, subLoungeId: UUID, request: ListeningStatusRequest) {
        requireActiveSubLoungeMember(userId, subLoungeId)
        jdbc.update(
            """
            INSERT INTO sub_lounge_listening_statuses(
              sub_lounge_id, user_id, track_title, artist_name, album_art_url, is_playing, expires_at
            )
            VALUES (?, ?, ?, ?, ?, ?, now() + interval '90 seconds')
            ON CONFLICT(sub_lounge_id, user_id) DO UPDATE SET
              track_title = excluded.track_title,
              artist_name = excluded.artist_name,
              album_art_url = excluded.album_art_url,
              is_playing = excluded.is_playing,
              updated_at = now(),
              expires_at = excluded.expires_at
            """.trimIndent(),
            subLoungeId,
            userId,
            request.trackTitle.trim().take(160),
            request.artistName.trim().take(160),
            request.albumArtUrl,
            request.isPlaying,
        )
    }

    fun addCard(userId: UUID, subLoungeId: UUID, request: RecommendationCardRequest): RecommendationCardSummary {
        requireActiveSubLoungeMember(userId, subLoungeId)
        val id = jdbc.query(
            """
            INSERT INTO sub_lounge_recommendation_cards(
              sub_lounge_id, sender_id, client_card_id, track_title, artist_name, message
            ) VALUES (?, ?, gen_random_uuid(), ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            subLoungeId,
            userId,
            request.trackTitle.trim().take(160),
            request.artistName.trim().take(160),
            request.message?.trim()?.take(240)?.ifBlank { null },
        ).first()
        return cards(subLoungeId).first { it.id == id }
    }

    fun cards(subLoungeId: UUID): List<RecommendationCardSummary> = jdbc.query(
        """
        SELECT c.id, c.sub_lounge_id, c.sender_id, c.track_title, c.artist_name, c.message, c.created_at,
               count(r.user_id) AS reaction_count
        FROM sub_lounge_recommendation_cards c
        LEFT JOIN sub_lounge_card_reactions r ON r.card_id = c.id
        WHERE c.sub_lounge_id = ?
        GROUP BY c.id
        ORDER BY c.created_at DESC
        LIMIT 50
        """.trimIndent(),
        { rs, _ ->
            RecommendationCardSummary(
                id = UUID.fromString(rs.getString("id")),
                subLoungeId = UUID.fromString(rs.getString("sub_lounge_id")),
                senderId = UUID.fromString(rs.getString("sender_id")),
                trackTitle = rs.getString("track_title"),
                artistName = rs.getString("artist_name"),
                message = rs.getString("message"),
                reactionCount = rs.getInt("reaction_count"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        },
        subLoungeId,
    )

    fun react(userId: UUID, cardId: UUID, request: ReactionRequest) {
        jdbc.update(
            """
            INSERT INTO sub_lounge_card_reactions(card_id, user_id, reaction_type)
            VALUES (?, ?, ?)
            ON CONFLICT(card_id, user_id, reaction_type) DO NOTHING
            """.trimIndent(),
            cardId,
            userId,
            request.reactionType.trim().uppercase().take(32).ifBlank { "LIKE" },
        )
    }

    private fun requireInside(loungeId: UUID, latitude: Double, longitude: Double): BuildingLoungeSummary {
        val lounge = loungeAt(loungeId, latitude, longitude)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Building lounge not found")
        if (!lounge.inside) throw ResponseStatusException(HttpStatus.FORBIDDEN, "You are outside this lounge area")
        return lounge
    }

    private fun requireCompatibleWifi(loungeId: UUID, wifiFingerprint: String) {
        val loungeMatchesWifi = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM building_lounges lounge
              JOIN lounge_buildings building ON building.id=lounge.building_id
              WHERE lounge.id=? AND lounge.active=true AND building.active=true
                AND (building.category<>'WIFI' OR building.google_place_id LIKE ?)
            )
            """.trimIndent(),
            Boolean::class.java,
            loungeId,
            "wifi-$wifiFingerprint-%",
        ) == true
        if (!loungeMatchesWifi) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Connect to the lounge Wi-Fi first")
        }
        val incompatible = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM building_lounge_sessions
              WHERE building_lounge_id=? AND active=true AND expires_at>now()
                AND wifi_fingerprint IS DISTINCT FROM ?
            )
            """.trimIndent(),
            Boolean::class.java,
            loungeId,
            wifiFingerprint,
        ) == true
        if (incompatible) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Connect to the lounge Wi-Fi first")
        }
    }

    private fun sessionUsesWifi(userId: UUID, loungeId: UUID, wifiFingerprint: String): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM building_lounge_sessions
              WHERE user_id=? AND building_lounge_id=? AND active=true AND expires_at>now()
                AND wifi_fingerprint=?
            )
            """.trimIndent(),
            Boolean::class.java,
            userId,
            loungeId,
            wifiFingerprint,
        ) == true

    private fun loungeAt(loungeId: UUID, latitude: Double, longitude: Double): BuildingLoungeSummary? {
        validateCoordinate(latitude, longitude)
        return jdbc.query(
            """
            SELECT bl.id, b.id AS building_id, b.name, b.address,
                   ST_Y(b.point) AS latitude, ST_X(b.point) AS longitude,
                   b.radius_m, b.category,
                   ST_DistanceSphere(b.point, ST_SetSRID(ST_MakePoint(?, ?), 4326)) AS distance_meters,
                   ST_DWithin(b.point::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, b.radius_m) AS inside,
                   count(DISTINCT s.user_id) FILTER (WHERE s.active = true AND s.expires_at > now()) AS active_members,
                   count(DISTINCT sl.id) FILTER (WHERE sl.active = true) AS sub_lounge_count
            FROM building_lounges bl
            JOIN lounge_buildings b ON b.id = bl.building_id AND b.active = true
            LEFT JOIN building_lounge_sessions s ON s.building_lounge_id = bl.id
            LEFT JOIN sub_lounges sl ON sl.building_lounge_id = bl.id
            WHERE bl.id = ? AND bl.active = true
            GROUP BY bl.id, b.id
            """.trimIndent(),
            { rs, _ -> rs.toBuildingLoungeSummary() },
            longitude,
            latitude,
            longitude,
            latitude,
            loungeId,
        ).firstOrNull()
    }

    private fun currentOutsideCount(userId: UUID, loungeId: UUID): Int = jdbc.query(
        "SELECT outside_count FROM building_lounge_sessions WHERE user_id=? AND building_lounge_id=?",
        { rs, _ -> rs.getInt("outside_count") },
        userId,
        loungeId,
    ).firstOrNull() ?: 0

    private fun activeSubLoungeCount(loungeId: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM sub_lounges WHERE building_lounge_id=? AND active=true",
        Int::class.java,
        loungeId,
    ) ?: 0

    private fun buildingLoungeIdForSubLounge(subLoungeId: UUID): UUID = jdbc.query(
        "SELECT building_lounge_id FROM sub_lounges WHERE id=? AND active=true",
        { rs, _ -> UUID.fromString(rs.getString("building_lounge_id")) },
        subLoungeId,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Sub lounge not found")

    private fun requireActiveBuildingSession(userId: UUID, loungeId: UUID) {
        val active = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM building_lounge_sessions
              WHERE user_id=? AND building_lounge_id=? AND active=true AND expires_at > now()
            )
            """.trimIndent(),
            Boolean::class.java,
            userId,
            loungeId,
        ) == true
        if (!active) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Enter the building lounge first")
    }

    private fun requireActiveSubLoungeMember(userId: UUID, subLoungeId: UUID) {
        val active = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM sub_lounge_members
              WHERE user_id=? AND sub_lounge_id=? AND active=true
            )
            """.trimIndent(),
            Boolean::class.java,
            userId,
            subLoungeId,
        ) == true
        if (!active) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Join the sub lounge first")
    }

    private fun validateCoordinate(latitude: Double, longitude: Double) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid coordinate")
        }
    }

    private fun validateWifiFingerprint(wifiFingerprint: String) {
        if (!WIFI_FINGERPRINT.matches(wifiFingerprint)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Wi-Fi fingerprint")
        }
    }

    private fun buildAddress(tags: JsonNode): String {
        val parts = listOf("addr:city", "addr:district", "addr:street", "addr:housenumber")
            .mapNotNull { key -> tags.path(key).asText(null)?.takeIf { it.isNotBlank() } }
        return parts.joinToString(" ").ifBlank { "주소 정보 없음" }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class DiscoveredBuilding(
        val placeId: String,
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int,
        val category: String,
    )

    private fun java.sql.ResultSet.toBuildingLoungeSummary(): BuildingLoungeSummary = BuildingLoungeSummary(
        id = UUID.fromString(getString("id")),
        buildingId = UUID.fromString(getString("building_id")),
        name = getString("name"),
        address = getString("address"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        radiusMeters = getInt("radius_m"),
        category = getString("category"),
        distanceMeters = getDouble("distance_meters"),
        inside = getBoolean("inside"),
        activeMembers = getInt("active_members"),
        subLoungeCount = getInt("sub_lounge_count"),
    )

    companion object {
        private val WIFI_FINGERPRINT = Regex("[0-9a-f]{64}")
        private const val MAX_SUB_LOUNGES = 5
        private const val WIFI_DISCOVERY_METERS = 1_500
    }
}

@RestController
@RequestMapping("/api/v1/building-lounges")
class BuildingLoungeController(
    private val service: BuildingLoungeService,
    private val realtimeService: SubLoungeRealtimeService,
) {
    @GetMapping("/nearby")
    fun nearby(
        principal: Principal,
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam wifiFingerprint: String,
        @RequestParam wifiName: String,
    ): List<BuildingLoungeSummary> = service.nearby(
        UUID.fromString(principal.name), latitude, longitude, wifiFingerprint, wifiName
    )

    @PostMapping("/{loungeId}/enter")
    fun enter(
        principal: Principal,
        @PathVariable loungeId: UUID,
        @RequestBody request: EnterBuildingLoungeRequest,
    ): BuildingLoungeSessionResponse = service.enter(UUID.fromString(principal.name), loungeId, request)

    @PostMapping("/{loungeId}/heartbeat")
    fun heartbeat(
        principal: Principal,
        @PathVariable loungeId: UUID,
        @RequestBody request: HeartbeatRequest,
    ): HeartbeatResponse = service.heartbeat(UUID.fromString(principal.name), loungeId, request)

    @PostMapping("/{loungeId}/leave")
    fun leave(principal: Principal, @PathVariable loungeId: UUID) {
        service.leave(UUID.fromString(principal.name), loungeId)
    }

    @GetMapping("/{loungeId}/sub-lounges")
    fun subLounges(@PathVariable loungeId: UUID): List<SubLoungeSummary> = service.subLounges(loungeId)

    @PostMapping("/{loungeId}/sub-lounges")
    fun createSubLounge(
        principal: Principal,
        @PathVariable loungeId: UUID,
        @RequestBody request: CreateSubLoungeRequest,
    ): SubLoungeSummary = service.createSubLounge(UUID.fromString(principal.name), loungeId, request)

    @PostMapping("/sub-lounges/{subLoungeId}/join")
    fun joinSubLounge(principal: Principal, @PathVariable subLoungeId: UUID): SubLoungeSummary =
        realtimeService.join(UUID.fromString(principal.name), subLoungeId).let {
            SubLoungeSummary(it.id, it.buildingLoungeId, it.title, it.style, it.memberCount, it.generatedAt)
        }

    @GetMapping("/sub-lounges/{subLoungeId}/cards")
    fun cards(principal: Principal, @PathVariable subLoungeId: UUID): List<LoungeRecommendationCard> =
        realtimeService.cards(UUID.fromString(principal.name), subLoungeId)

    @PostMapping("/sub-lounges/{subLoungeId}/cards")
    fun addCard(
        principal: Principal,
        @PathVariable subLoungeId: UUID,
        @RequestBody request: CreateLoungeCardRequest,
    ): LoungeRecommendationCard = realtimeService.addCard(UUID.fromString(principal.name), subLoungeId, request)

    @PostMapping("/cards/{cardId}/reactions")
    fun react(
        principal: Principal,
        @PathVariable cardId: UUID,
        @RequestBody request: ReactionRequest,
    ): LoungeRecommendationCard = realtimeService.react(UUID.fromString(principal.name), cardId, request)
}

@Controller
class BuildingLoungeMessageController(
    private val service: BuildingLoungeService,
    private val messaging: SimpMessagingTemplate,
) {
    @org.springframework.messaging.handler.annotation.MessageMapping("building-lounge/sub-listening")
    fun updateListening(update: SubLoungeListeningMessage, principal: Principal) {
        val userId = UUID.fromString(principal.name)
        val subLoungeId = update.subLoungeId
        service.updateListening(
            userId,
            subLoungeId,
            ListeningStatusRequest(update.trackTitle, update.artistName, update.albumArtUrl, update.isPlaying),
        )
        messaging.convertAndSend(
            "/topic/sub-lounges/$subLoungeId",
            RealtimeEnvelope(type = "LISTENING_STATUS_UPDATED", payload = update),
        )
    }
}

data class SubLoungeListeningMessage(
    val subLoungeId: UUID,
    val trackTitle: String,
    val artistName: String,
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = true,
)
