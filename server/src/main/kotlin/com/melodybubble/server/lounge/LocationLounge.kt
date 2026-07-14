package com.melodybubble.server.lounge

import com.melodybubble.server.realtime.RealtimeEventTypes
import com.melodybubble.server.realtime.RealtimePublisher
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

enum class LocationLoungeStatus { ACTIVE, MERGING, DELETING, DELETED }

data class LocationLoungeSummary(
    val loungeId: UUID,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radius: Int,
    val currentUserCount: Int,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: LocationLoungeStatus,
    val available: Boolean,
)

data class CreateLoungeChatRoomRequest(val title: String)
data class SendLoungeChatMessageRequest(val clientMessageId: UUID, val content: String)
data class LoungeChatRoomSummary(
    val chatRoomId: UUID,
    val loungeId: UUID,
    val ownerId: UUID,
    val title: String,
    val joined: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: String,
)
data class LoungeChatMessage(
    val id: UUID,
    val chatRoomId: UUID,
    val senderId: UUID,
    val content: String,
    val sentAt: Instant,
)
data class LoungeMergedPayload(
    val deletedLoungeId: UUID,
    val targetLoungeId: UUID,
    val movedChatRoomIds: List<UUID>,
    val reason: String = "OVERLAP_THRESHOLD",
)
private data class UserPoint(val latitude: Double, val longitude: Double)

@Service
class LocationLoungeService(
    private val jdbc: JdbcTemplate,
    private val realtime: RealtimePublisher,
) {
    @Transactional(readOnly = true)
    fun snapshot(userId: UUID, latitude: Double, longitude: Double): List<LocationLoungeSummary> {
        validateCoordinates(latitude, longitude)
        return jdbc.query(
            """
            SELECT id,ST_Y(center) latitude,ST_X(center) longitude,radius_m,current_user_count,
                   created_by,created_at,updated_at,status,
                   ST_DWithin(center::geography,ST_SetSRID(ST_MakePoint(?,?),4326)::geography,radius_m) available
            FROM location_lounges WHERE status='ACTIVE' ORDER BY created_at,id
            """.trimIndent(),
            { rs, _ -> rs.toSummary() }, longitude, latitude,
        )
    }

    @Transactional
    fun create(userId: UUID): LocationLoungeSummary {
        advisoryLock("location-lounge-create:$userId")
        advisoryLock(GLOBAL_RECONCILE_LOCK)
        val point = latestLocation(userId)
            ?: throw ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "최신 위치 정보가 필요합니다.")
        val containingLoungeCount = jdbc.queryForObject(
            """
            SELECT count(*) FROM location_lounges
            WHERE status='ACTIVE' AND ST_DWithin(
              center::geography,ST_SetSRID(ST_MakePoint(?,?),4326)::geography,radius_m
            )
            """.trimIndent(),
            Int::class.java, point.longitude, point.latitude,
        ) ?: 0
        if (!LocationLoungePolicy.canCreateLounge(containingLoungeCount)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "현재 라운지 안에서는 새 라운지를 만들 수 없습니다.")
        }
        val id = jdbc.query(
            """
            INSERT INTO location_lounges(center,radius_m,current_user_count,created_by)
            VALUES (ST_SetSRID(ST_MakePoint(?,?),4326),?,1,?) RETURNING id
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            point.longitude,
            point.latitude,
            LocationLoungePolicy.INITIAL_RADIUS_METERS,
            userId,
        ).single()
        jdbc.update(
            "INSERT INTO location_lounge_presence_cache(lounge_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",
            id, userId,
        )
        val created = requireLounge(id)
        publish(RealtimeEventTypes.LOUNGE_CREATED, created)
        publish(RealtimeEventTypes.USER_ENTERED_LOUNGE, mapOf("loungeId" to id, "userId" to userId))
        publish(RealtimeEventTypes.LOUNGE_USER_COUNT_CHANGED, countPayload(id, 1))
        return created
    }

    /** Called in the same transaction immediately after current_locations changes. */
    fun reconcileAfterLocationChange() = reconcileLocked()

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    fun reconcileExpiredLocationsAndDeleteEmptyLounges() = reconcileLocked()

    private fun reconcileLocked() {
        advisoryLock(GLOBAL_RECONCILE_LOCK)
        val lounges = activeDisksForUpdate()
        lounges.forEach { disk -> reconcilePopulation(disk) }
        mergeOverlaps()
        deleteUnderpopulated()
    }

    private fun reconcilePopulation(disk: LoungeDisk) {
        val distances = activeUserDistances(disk.id)
        val stableRadius = LocationLoungePolicy.effectiveRadius(
            currentRadius = disk.radiusMeters,
            distancesMeters = distances.values,
            createdAt = disk.createdAt,
            now = Instant.now(),
        )
        val users = distances.filterValues { it <= stableRadius + DISTANCE_EPSILON }.keys
        val previous = jdbc.query(
            "SELECT user_id FROM location_lounge_presence_cache WHERE lounge_id=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, disk.id,
        ).toSet()
        val previousCount = jdbc.queryForObject(
            "SELECT current_user_count FROM location_lounges WHERE id=?", Int::class.java, disk.id,
        ) ?: 0
        if (stableRadius != disk.radiusMeters || previousCount != users.size) {
            jdbc.update(
                "UPDATE location_lounges SET radius_m=?,current_user_count=?,updated_at=now() WHERE id=? AND status='ACTIVE'",
                stableRadius, users.size, disk.id,
            )
        }
        if (stableRadius != disk.radiusMeters) {
            publish(
                RealtimeEventTypes.LOUNGE_RADIUS_CHANGED,
                mapOf("loungeId" to disk.id, "previousRadius" to disk.radiusMeters, "radius" to stableRadius),
            )
        }
        if (previousCount != users.size) {
            publish(RealtimeEventTypes.LOUNGE_USER_COUNT_CHANGED, countPayload(disk.id, users.size))
        }
        (users - previous).forEach { userId ->
            publish(RealtimeEventTypes.USER_ENTERED_LOUNGE, mapOf("loungeId" to disk.id, "userId" to userId))
        }
        (previous - users).forEach { userId ->
            publish(RealtimeEventTypes.USER_LEFT_LOUNGE, mapOf("loungeId" to disk.id, "userId" to userId))
        }
        jdbc.update("DELETE FROM location_lounge_presence_cache WHERE lounge_id=?", disk.id)
        users.forEach { userId ->
            jdbc.update(
                "INSERT INTO location_lounge_presence_cache(lounge_id,user_id) VALUES (?,?)", disk.id, userId,
            )
        }
    }

    private fun mergeOverlaps() {
        repeat(MAX_MERGES_PER_PASS) {
            val disks = activeDisksForUpdate()
            val decisions = disks.mapNotNull { LocationLoungePolicy.chooseMerge(it, disks) }
            val decision = decisions.sortedWith(
                compareByDescending<LoungeMergeDecision> { it.overlapRatio }
                    .thenBy { it.deletedId.toString() },
            ).firstOrNull() ?: return
            val targetStillActive = disks.any { it.id == decision.targetId }
            if (!targetStillActive) return@repeat
            merge(decision.deletedId, decision.targetId)
        }
    }

    private fun merge(sourceId: UUID, targetId: UUID) {
        val claimed = jdbc.update(
            "UPDATE location_lounges SET status='MERGING',updated_at=now() WHERE id=? AND status='ACTIVE'",
            sourceId,
        )
        if (claimed != 1) return
        jdbc.query("SELECT id FROM location_lounges WHERE id IN (?,?) FOR UPDATE", { _, _ -> Unit }, sourceId, targetId)
        val moved = jdbc.query(
            "SELECT id FROM location_lounge_chat_rooms WHERE lounge_id=? AND status='ACTIVE' ORDER BY created_at,id",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, sourceId,
        )
        jdbc.update(
            "UPDATE location_lounge_chat_rooms SET lounge_id=?,updated_at=now() WHERE lounge_id=?",
            targetId, sourceId,
        )
        jdbc.update("DELETE FROM location_lounge_presence_cache WHERE lounge_id=?", sourceId)
        jdbc.update(
            "UPDATE location_lounges SET status='DELETED',current_user_count=0,updated_at=now() WHERE id=? AND status='MERGING'",
            sourceId,
        )
        moved.forEach { roomId ->
            publish(
                RealtimeEventTypes.CHAT_ROOM_MOVED,
                mapOf("chatRoomId" to roomId, "deletedLoungeId" to sourceId, "targetLoungeId" to targetId),
            )
        }
        publish(
            RealtimeEventTypes.LOUNGE_MERGED,
            LoungeMergedPayload(sourceId, targetId, moved),
        )
        publish(RealtimeEventTypes.LOUNGE_DELETED, mapOf("loungeId" to sourceId, "reason" to "MERGED"))
        activeDisksForUpdate().firstOrNull { it.id == targetId }?.let(::reconcilePopulation)
    }

    private fun deleteUnderpopulated() {
        val ids = jdbc.query(
            """
            SELECT id FROM location_lounges
            WHERE status='ACTIVE' AND current_user_count<=2 AND created_at<=now()-interval '1 minute'
            FOR UPDATE
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString(1)) },
        )
        ids.forEach { id ->
            if (jdbc.update(
                    "UPDATE location_lounges SET status='DELETING',updated_at=now() WHERE id=? AND status='ACTIVE'",
                    id,
                ) != 1
            ) return@forEach
            val rooms = jdbc.query(
                "SELECT id FROM location_lounge_chat_rooms WHERE lounge_id=? AND status='ACTIVE'",
                { rs, _ -> UUID.fromString(rs.getString(1)) }, id,
            )
            jdbc.update(
                "UPDATE location_lounge_chat_rooms SET status='DELETED',updated_at=now() WHERE lounge_id=?",
                id,
            )
            jdbc.update("DELETE FROM location_lounge_presence_cache WHERE lounge_id=?", id)
            jdbc.update(
                "UPDATE location_lounges SET status='DELETED',current_user_count=0,updated_at=now() WHERE id=? AND status='DELETING'",
                id,
            )
            rooms.forEach { publish(RealtimeEventTypes.CHAT_ROOM_DELETED, mapOf("chatRoomId" to it, "loungeId" to id)) }
            publish(RealtimeEventTypes.LOUNGE_DELETED, mapOf("loungeId" to id, "reason" to "INSUFFICIENT_USERS"))
        }
    }

    @Transactional(readOnly = true)
    fun chatRooms(userId: UUID, loungeId: UUID): List<LoungeChatRoomSummary> {
        requireInside(userId, loungeId)
        return chatRoomsUnchecked(userId, loungeId)
    }

    @Transactional
    fun createChatRoom(userId: UUID, loungeId: UUID, request: CreateLoungeChatRoomRequest): LoungeChatRoomSummary {
        requireInside(userId, loungeId)
        jdbc.query("SELECT id FROM location_lounges WHERE id=? FOR UPDATE", { _, _ -> Unit }, loungeId)
        requireActiveLounge(loungeId)
        val count = jdbc.queryForObject(
            "SELECT count(*) FROM location_lounge_chat_rooms WHERE lounge_id=? AND status='ACTIVE'",
            Int::class.java, loungeId,
        ) ?: 0
        if (!LocationLoungePolicy.canCreateChatRoom(count, LocationLoungeStatus.ACTIVE)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "채팅방은 라운지당 최대 5개까지 만들 수 있습니다.")
        }
        val title = request.title.trim().take(80)
        if (title.length < 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "채팅방 이름은 2자 이상이어야 합니다.")
        val id = jdbc.query(
            "INSERT INTO location_lounge_chat_rooms(lounge_id,owner_id,title) VALUES (?,?,?) RETURNING id",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, loungeId, userId, title,
        ).single()
        jdbc.update(
            "INSERT INTO location_lounge_chat_members(chat_room_id,user_id) VALUES (?,?)",
            id, userId,
        )
        val room = chatRoomsUnchecked(userId, loungeId).first { it.chatRoomId == id }
        publish(RealtimeEventTypes.CHAT_ROOM_CREATED, room)
        return room
    }

    @Transactional
    fun joinChatRoom(userId: UUID, roomId: UUID): LoungeChatRoomSummary {
        val loungeId = loungeIdForRoom(roomId)
        requireInside(userId, loungeId)
        jdbc.update(
            """
            INSERT INTO location_lounge_chat_members(chat_room_id,user_id,active) VALUES (?,?,true)
            ON CONFLICT(chat_room_id,user_id) DO UPDATE SET active=true,updated_at=now()
            """.trimIndent(), roomId, userId,
        )
        return chatRoomsUnchecked(userId, loungeId).first { it.chatRoomId == roomId }
    }

    @Transactional
    fun leaveChatRoom(userId: UUID, roomId: UUID) {
        jdbc.update(
            "UPDATE location_lounge_chat_members SET active=false,updated_at=now() WHERE chat_room_id=? AND user_id=?",
            roomId, userId,
        )
    }

    @Transactional
    fun deleteChatRoom(userId: UUID, roomId: UUID) {
        val changed = jdbc.update(
            "UPDATE location_lounge_chat_rooms SET status='DELETED',updated_at=now() WHERE id=? AND owner_id=? AND status='ACTIVE'",
            roomId, userId,
        )
        if (changed != 1) throw ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 채팅방을 삭제할 수 있습니다.")
        publish(RealtimeEventTypes.CHAT_ROOM_DELETED, mapOf("chatRoomId" to roomId))
    }

    @Transactional
    fun sendMessage(userId: UUID, roomId: UUID, request: SendLoungeChatMessageRequest): LoungeChatMessage {
        val loungeId = loungeIdForRoom(roomId)
        requireInside(userId, loungeId)
        requireJoined(userId, roomId)
        val content = request.content.trim().take(1000)
        if (content.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지가 비어 있습니다.")
        val message = jdbc.query(
            """
            INSERT INTO location_lounge_chat_messages(chat_room_id,sender_id,client_message_id,content)
            VALUES (?,?,?,?) ON CONFLICT(sender_id,client_message_id) DO UPDATE SET content=excluded.content
            RETURNING id,chat_room_id,sender_id,content,sent_at
            """.trimIndent(),
            { rs, _ -> rs.toChatMessage() }, roomId, userId, request.clientMessageId, content,
        ).single()
        publish(RealtimeEventTypes.CHAT_MESSAGE_CREATED, message)
        return message
    }

    @Transactional(readOnly = true)
    fun messages(userId: UUID, roomId: UUID): List<LoungeChatMessage> {
        val loungeId = loungeIdForRoom(roomId)
        requireInside(userId, loungeId)
        requireJoined(userId, roomId)
        return jdbc.query(
            """
            SELECT id,chat_room_id,sender_id,content,sent_at FROM location_lounge_chat_messages
            WHERE chat_room_id=? ORDER BY sent_at DESC LIMIT 100
            """.trimIndent(),
            { rs, _ -> rs.toChatMessage() }, roomId,
        )
    }

    private fun chatRoomsUnchecked(userId: UUID, loungeId: UUID) = jdbc.query(
        """
        SELECT room.id,room.lounge_id,room.owner_id,room.title,room.created_at,room.updated_at,room.status,
               coalesce(member.active,false) joined
        FROM location_lounge_chat_rooms room
        LEFT JOIN location_lounge_chat_members member ON member.chat_room_id=room.id AND member.user_id=?
        WHERE room.lounge_id=? AND room.status='ACTIVE' ORDER BY room.created_at,room.id
        """.trimIndent(),
        { rs, _ ->
            LoungeChatRoomSummary(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("lounge_id")),
                UUID.fromString(rs.getString("owner_id")), rs.getString("title"), rs.getBoolean("joined"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("status"),
            )
        }, userId, loungeId,
    )

    private fun activeDisksForUpdate(): List<LoungeDisk> = jdbc.query(
        """
        SELECT id,ST_Y(center) latitude,ST_X(center) longitude,radius_m,created_at
        FROM location_lounges WHERE status='ACTIVE' ORDER BY created_at,id FOR UPDATE
        """.trimIndent(),
        { rs, _ ->
            LoungeDisk(
                UUID.fromString(rs.getString("id")), rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getInt("radius_m"), rs.getTimestamp("created_at").toInstant(),
            )
        },
    )

    private fun activeUserDistances(loungeId: UUID): Map<UUID, Double> = jdbc.query(
        """
        WITH latest AS (
          SELECT DISTINCT ON (session.user_id) session.user_id,location.point
          FROM presence_sessions session JOIN current_locations location ON location.session_id=session.id
          WHERE session.expires_at>now() AND location.expires_at>now()
          ORDER BY session.user_id,location.updated_at DESC,session.last_seen_at DESC
        )
        SELECT latest.user_id,ST_DistanceSphere(latest.point,lounge.center) distance_m
        FROM latest CROSS JOIN location_lounges lounge
        WHERE lounge.id=? AND ST_DWithin(latest.point::geography,lounge.center::geography,20)
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString("user_id")) to rs.getDouble("distance_m") }, loungeId,
    ).toMap()

    private fun latestLocation(userId: UUID): UserPoint? = jdbc.query(
        """
        SELECT ST_Y(location.point) latitude,ST_X(location.point) longitude FROM presence_sessions session
        JOIN current_locations location ON location.session_id=session.id
        WHERE session.user_id=? AND session.expires_at>now() AND location.expires_at>now()
        ORDER BY location.updated_at DESC,session.last_seen_at DESC LIMIT 1
        """.trimIndent(),
        { rs, _ -> UserPoint(rs.getDouble("latitude"), rs.getDouble("longitude")) }, userId,
    ).firstOrNull()

    private fun requireInside(userId: UUID, loungeId: UUID) {
        val inside = jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM location_lounges lounge
              JOIN LATERAL (
                SELECT location.point FROM presence_sessions session
                JOIN current_locations location ON location.session_id=session.id
                WHERE session.user_id=? AND session.expires_at>now() AND location.expires_at>now()
                ORDER BY location.updated_at DESC LIMIT 1
              ) current ON true
              WHERE lounge.id=? AND lounge.status='ACTIVE'
                AND ST_DWithin(current.point::geography,lounge.center::geography,lounge.radius_m)
            )
            """.trimIndent(),
            Boolean::class.java, userId, loungeId,
        ) == true
        if (!inside) throw ResponseStatusException(HttpStatus.FORBIDDEN, "현재 라운지 반경 안에서만 이용할 수 있습니다.")
    }

    private fun requireJoined(userId: UUID, roomId: UUID) {
        val joined = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM location_lounge_chat_members WHERE chat_room_id=? AND user_id=? AND active=true)",
            Boolean::class.java, roomId, userId,
        ) == true
        if (!joined) throw ResponseStatusException(HttpStatus.FORBIDDEN, "가입한 채팅방만 이용할 수 있습니다.")
    }

    private fun loungeIdForRoom(roomId: UUID): UUID = jdbc.query(
        "SELECT lounge_id FROM location_lounge_chat_rooms WHERE id=? AND status='ACTIVE'",
        { rs, _ -> UUID.fromString(rs.getString(1)) }, roomId,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다.")

    private fun requireActiveLounge(id: UUID) {
        val active = jdbc.queryForObject(
            "SELECT status='ACTIVE' FROM location_lounges WHERE id=?", Boolean::class.java, id,
        ) == true
        if (!active) throw ResponseStatusException(HttpStatus.CONFLICT, "처리 중이거나 삭제된 라운지입니다.")
    }

    private fun requireLounge(id: UUID): LocationLoungeSummary = jdbc.query(
        """
        SELECT id,ST_Y(center) latitude,ST_X(center) longitude,radius_m,current_user_count,
               created_by,created_at,updated_at,status,true available FROM location_lounges WHERE id=?
        """.trimIndent(),
        { rs, _ -> rs.toSummary() }, id,
    ).single()

    private fun ResultSet.toSummary() = LocationLoungeSummary(
        UUID.fromString(getString("id")), getDouble("latitude"), getDouble("longitude"),
        getInt("radius_m"), getInt("current_user_count"), UUID.fromString(getString("created_by")),
        getTimestamp("created_at").toInstant(), getTimestamp("updated_at").toInstant(),
        LocationLoungeStatus.valueOf(getString("status")), getBoolean("available"),
    )

    private fun ResultSet.toChatMessage() = LoungeChatMessage(
        UUID.fromString(getString("id")), UUID.fromString(getString("chat_room_id")),
        UUID.fromString(getString("sender_id")), getString("content"), getTimestamp("sent_at").toInstant(),
    )

    private fun advisoryLock(key: String) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", { _, _ -> Unit }, key)
    }

    private fun publish(type: String, payload: Any) =
        realtime.toTopicAfterCommit(LOUNGE_TOPIC, type, payload)

    private fun countPayload(id: UUID, count: Int) = mapOf("loungeId" to id, "currentUserCount" to count)

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 위치가 필요합니다.")
    }

    companion object {
        const val LOUNGE_TOPIC = "/topic/location-lounges"
        private const val GLOBAL_RECONCILE_LOCK = "location-lounges:reconcile"
        private const val MAX_MERGES_PER_PASS = 64
        private const val DISTANCE_EPSILON = 0.05
    }
}

@RestController
@RequestMapping("/api/v1/location-lounges")
class LocationLoungeController(private val service: LocationLoungeService) {
    @GetMapping
    fun snapshot(
        principal: Principal,
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
    ) = service.snapshot(UUID.fromString(principal.name), latitude, longitude)

    @PostMapping
    fun create(principal: Principal) = service.create(UUID.fromString(principal.name))

    @GetMapping("/{loungeId}/chat-rooms")
    fun chatRooms(principal: Principal, @PathVariable loungeId: UUID) =
        service.chatRooms(UUID.fromString(principal.name), loungeId)

    @PostMapping("/{loungeId}/chat-rooms")
    fun createChatRoom(
        principal: Principal,
        @PathVariable loungeId: UUID,
        @RequestBody request: CreateLoungeChatRoomRequest,
    ) = service.createChatRoom(UUID.fromString(principal.name), loungeId, request)

    @PostMapping("/chat-rooms/{roomId}/join")
    fun join(principal: Principal, @PathVariable roomId: UUID) =
        service.joinChatRoom(UUID.fromString(principal.name), roomId)

    @PostMapping("/chat-rooms/{roomId}/leave")
    fun leave(principal: Principal, @PathVariable roomId: UUID) =
        service.leaveChatRoom(UUID.fromString(principal.name), roomId)

    @DeleteMapping("/chat-rooms/{roomId}")
    fun delete(principal: Principal, @PathVariable roomId: UUID) =
        service.deleteChatRoom(UUID.fromString(principal.name), roomId)

    @GetMapping("/chat-rooms/{roomId}/messages")
    fun messages(principal: Principal, @PathVariable roomId: UUID) =
        service.messages(UUID.fromString(principal.name), roomId)

    @PostMapping("/chat-rooms/{roomId}/messages")
    fun send(
        principal: Principal,
        @PathVariable roomId: UUID,
        @RequestBody request: SendLoungeChatMessageRequest,
    ) = service.sendMessage(UUID.fromString(principal.name), roomId, request)
}
