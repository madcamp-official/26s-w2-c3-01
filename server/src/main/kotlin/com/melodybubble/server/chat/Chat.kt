package com.melodybubble.server.chat

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Instant
import java.util.UUID

data class ChatRoomSummaryResponse(
    val roomId: UUID,
    val peerHandle: String,
    val peerAlias: String,
    val peerColorHex: String,
    val lastMessage: String,
    val lastMessageAt: Instant?,
    val unreadCount: Int = 0,
    val relationship: String,
)

data class ChatMessageResponse(
    val messageId: UUID,
    val clientMessageId: UUID?,
    val roomId: UUID,
    val senderId: UUID,
    val isMine: Boolean,
    val content: String,
    val sentAt: Instant,
)

data class SendChatMessageRequest(
    val clientMessageId: UUID? = null,
    val content: String,
)

@Service
class ChatService(private val jdbc: JdbcTemplate) {
    fun openDirectRoom(userId: UUID, nearbyHandle: String): ChatRoomSummaryResponse {
        val peerId = resolveNearbyUser(nearbyHandle)
        if (userId == peerId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot chat with yourself")
        }
        ensureMutualUsers(userId, peerId)
        val roomId = ensureDirectChat(userId, peerId)
        return listRooms(userId).firstOrNull { it.roomId == roomId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room was not found")
    }

    fun listRooms(userId: UUID): List<ChatRoomSummaryResponse> = jdbc.query(
        """
        SELECT cr.id AS room_id,
               peer.id AS peer_id,
               COALESCE(ps.nearby_handle, 'user-' || peer.id::text) AS peer_handle,
               peer.display_name AS peer_alias,
               peer.profile_color AS peer_color_hex,
               COALESCE(last_message.content, '서로 팔로우했어요') AS last_message,
               last_message.sent_at AS last_message_at,
               EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=? AND uf.followee_id=peer.id) AS following,
               EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=peer.id AND uf.followee_id=?) AS follows_me
        FROM chat_room_members mine
        JOIN chat_rooms cr ON cr.id = mine.room_id AND cr.type = 'DIRECT'
        JOIN chat_room_members peer_member ON peer_member.room_id = mine.room_id AND peer_member.user_id <> mine.user_id
        JOIN users peer ON peer.id = peer_member.user_id
        LEFT JOIN LATERAL (
          SELECT nearby_handle
          FROM presence_sessions
          WHERE user_id = peer.id AND expires_at > now()
          ORDER BY last_seen_at DESC
          LIMIT 1
        ) ps ON true
        LEFT JOIN LATERAL (
          SELECT content, sent_at
          FROM chat_messages
          WHERE room_id = cr.id
          ORDER BY sent_at DESC
          LIMIT 1
        ) last_message ON true
        WHERE mine.user_id = ?
          AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id=? AND ub.blocked_id=peer.id)
               OR (ub.blocker_id=peer.id AND ub.blocked_id=?)
          )
        ORDER BY last_message.sent_at DESC NULLS LAST, cr.created_at DESC
        """.trimIndent(),
        { rs, _ ->
            ChatRoomSummaryResponse(
                roomId = UUID.fromString(rs.getString("room_id")),
                peerHandle = rs.getString("peer_handle"),
                peerAlias = rs.getString("peer_alias"),
                peerColorHex = rs.getString("peer_color_hex"),
                lastMessage = rs.getString("last_message"),
                lastMessageAt = rs.getTimestamp("last_message_at")?.toInstant(),
                relationship = relationship(rs.getBoolean("following"), rs.getBoolean("follows_me")),
            )
        },
        userId,
        userId,
        userId,
        userId,
        userId,
    )

    fun listMessages(userId: UUID, roomId: UUID): List<ChatMessageResponse> {
        ensureRoomMember(userId, roomId)
        ensureMutualDirectRoom(userId, roomId)
        return jdbc.query(
            """
            SELECT id, room_id, sender_id, client_message_id, content, sent_at
            FROM chat_messages
            WHERE room_id = ?
            ORDER BY sent_at ASC
            LIMIT 100
            """.trimIndent(),
            { rs, _ ->
                val senderId = UUID.fromString(rs.getString("sender_id"))
                ChatMessageResponse(
                    messageId = UUID.fromString(rs.getString("id")),
                    clientMessageId = rs.getString("client_message_id")?.let(UUID::fromString),
                    roomId = UUID.fromString(rs.getString("room_id")),
                    senderId = senderId,
                    isMine = senderId == userId,
                    content = rs.getString("content"),
                    sentAt = rs.getTimestamp("sent_at").toInstant(),
                )
            },
            roomId,
        )
    }

    fun sendMessage(userId: UUID, roomId: UUID, request: SendChatMessageRequest): ChatMessageResponse {
        ensureRoomMember(userId, roomId)
        ensureMutualDirectRoom(userId, roomId)
        val content = request.content.trim().take(1000)
        if (content.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required")
        }
        val message = jdbc.query(
            """
            INSERT INTO chat_messages(id, room_id, sender_id, client_message_id, content)
            VALUES (gen_random_uuid(), ?, ?, ?, ?)
            ON CONFLICT(sender_id, client_message_id) DO UPDATE SET content = chat_messages.content
            RETURNING id, room_id, sender_id, client_message_id, content, sent_at
            """.trimIndent(),
            { rs, _ ->
                ChatMessageResponse(
                    messageId = UUID.fromString(rs.getString("id")),
                    clientMessageId = rs.getString("client_message_id")?.let(UUID::fromString),
                    roomId = UUID.fromString(rs.getString("room_id")),
                    senderId = UUID.fromString(rs.getString("sender_id")),
                    isMine = true,
                    content = rs.getString("content"),
                    sentAt = rs.getTimestamp("sent_at").toInstant(),
                )
            },
            roomId,
            userId,
            request.clientMessageId,
            content,
        ).first()
        jdbc.update(
            "UPDATE chat_room_members SET last_read_message_id=? WHERE room_id=? AND user_id=?",
            message.messageId,
            roomId,
            userId,
        )
        return message
    }

    private fun ensureRoomMember(userId: UUID, roomId: UUID) {
        val member = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM chat_room_members WHERE room_id=? AND user_id=?)",
            Boolean::class.java,
            roomId,
            userId,
        ) == true
        if (!member) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room was not found")
        }
    }

    private fun resolveNearbyUser(handle: String): UUID = jdbc.query(
        """
        SELECT user_id
        FROM presence_sessions
        WHERE nearby_handle = ? AND expires_at > now()
        ORDER BY last_seen_at DESC
        LIMIT 1
        """.trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString("user_id")) },
        handle,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Nearby user is no longer available")

    private fun ensureMutualUsers(userId: UUID, peerId: UUID) {
        val available = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              WHERE EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=? AND uf.followee_id=?)
                AND EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=? AND uf.followee_id=?)
                AND NOT EXISTS (
                  SELECT 1 FROM user_blocks ub
                  WHERE (ub.blocker_id=? AND ub.blocked_id=?)
                     OR (ub.blocker_id=? AND ub.blocked_id=?)
                )
            )
            """.trimIndent(),
            Boolean::class.java,
            userId,
            peerId,
            peerId,
            userId,
            userId,
            peerId,
            peerId,
            userId,
        ) == true
        if (!available) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Chat is only available between mutual follows")
        }
    }

    private fun ensureDirectChat(firstUserId: UUID, secondUserId: UUID): UUID {
        val existing = jdbc.query(
            """
            SELECT crm1.room_id
            FROM chat_room_members crm1
            JOIN chat_room_members crm2 ON crm2.room_id = crm1.room_id
            JOIN chat_rooms cr ON cr.id = crm1.room_id AND cr.type = 'DIRECT'
            WHERE crm1.user_id = ? AND crm2.user_id = ?
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("room_id")) },
            firstUserId,
            secondUserId,
        ).firstOrNull()
        if (existing != null) return existing

        val roomId = jdbc.query(
            "INSERT INTO chat_rooms(id, type) VALUES (gen_random_uuid(), 'DIRECT') RETURNING id",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
        ).first()
        jdbc.update(
            "INSERT INTO chat_room_members(room_id, user_id) VALUES (?, ?), (?, ?)",
            roomId,
            firstUserId,
            roomId,
            secondUserId,
        )
        return roomId
    }

    private fun ensureMutualDirectRoom(userId: UUID, roomId: UUID) {
        val available = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM chat_room_members mine
              JOIN chat_room_members peer ON peer.room_id = mine.room_id AND peer.user_id <> mine.user_id
              JOIN chat_rooms cr ON cr.id = mine.room_id AND cr.type = 'DIRECT'
              WHERE mine.room_id=? AND mine.user_id=?
                AND EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=mine.user_id AND uf.followee_id=peer.user_id)
                AND EXISTS (SELECT 1 FROM user_follows uf WHERE uf.follower_id=peer.user_id AND uf.followee_id=mine.user_id)
                AND NOT EXISTS (
                  SELECT 1 FROM user_blocks ub
                  WHERE (ub.blocker_id=mine.user_id AND ub.blocked_id=peer.user_id)
                     OR (ub.blocker_id=peer.user_id AND ub.blocked_id=mine.user_id)
                )
            )
            """.trimIndent(),
            Boolean::class.java,
            roomId,
            userId,
        ) == true
        if (!available) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Chat is only available between mutual follows")
        }
    }

    private fun relationship(following: Boolean, followsMe: Boolean) = when {
        following && followsMe -> "MUTUAL"
        following -> "FOLLOWING"
        followsMe -> "FOLLOWS_ME"
        else -> "NONE"
    }
}

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(private val service: ChatService) {
    @GetMapping
    fun listRooms(principal: Principal): List<ChatRoomSummaryResponse> =
        service.listRooms(UUID.fromString(principal.name))

    @PostMapping("/direct/{nearbyHandle}")
    fun openDirectRoom(principal: Principal, @PathVariable nearbyHandle: String): ChatRoomSummaryResponse =
        service.openDirectRoom(UUID.fromString(principal.name), nearbyHandle)

    @GetMapping("/{roomId}/messages")
    fun listMessages(principal: Principal, @PathVariable roomId: UUID): List<ChatMessageResponse> =
        service.listMessages(UUID.fromString(principal.name), roomId)

    @PostMapping("/{roomId}/messages")
    fun sendMessage(
        principal: Principal,
        @PathVariable roomId: UUID,
        @RequestBody request: SendChatMessageRequest,
    ): ChatMessageResponse = service.sendMessage(UUID.fromString(principal.name), roomId, request)
}
