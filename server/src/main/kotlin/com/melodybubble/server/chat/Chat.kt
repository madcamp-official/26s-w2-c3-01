package com.melodybubble.server.chat

import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class DirectChatSummary(
    val roomId: UUID,
    val peerHandle: String?,
    val peerAlias: String,
    val peerColor: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val unreadCount: Int,
    val relationship: String = "MUTUAL",
)

data class DirectChatMessage(
    val messageId: UUID,
    val clientMessageId: UUID?,
    val roomId: UUID,
    val isMine: Boolean,
    val content: String,
    val sentAt: Instant,
)

data class SendChatMessageRequest(val clientMessageId: String, val content: String)

@Service
class ChatService(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: ActionRateLimiter,
) {
    fun rooms(userId: UUID): List<DirectChatSummary> = jdbc.query(
        """
        select pair.room_id,
          (select ps.nearby_handle from presence_sessions ps
           where ps.user_id=peer.id and ps.expires_at>now()
           order by ps.last_seen_at desc limit 1) peer_handle,
          peer.display_name,peer.profile_color,last_message.content,last_message.sent_at,
          (select count(*) from chat_messages unread
           where unread.room_id=pair.room_id and unread.sender_id<>?
             and (member.last_read_message_id is null or unread.sent_at > coalesce(
               (select sent_at from chat_messages where id=member.last_read_message_id), '-infinity'
             ))) unread_count
        from direct_chat_pairs pair
        join chat_room_members member on member.room_id=pair.room_id and member.user_id=?
        join users peer on peer.id=case when pair.first_user_id=? then pair.second_user_id else pair.first_user_id end
        join user_follows mine on mine.follower_id=? and mine.followed_id=peer.id
        join user_follows theirs on theirs.follower_id=peer.id and theirs.followed_id=?
        left join lateral (
          select content,sent_at from chat_messages message
          where message.room_id=pair.room_id order by sent_at desc limit 1
        ) last_message on true
        where not exists(
          select 1 from user_blocks block
          where (block.blocker_id=? and block.blocked_id=peer.id)
             or (block.blocker_id=peer.id and block.blocked_id=?)
        )
        order by last_message.sent_at desc nulls last, pair.created_at desc
        """.trimIndent(),
        { rs, _ ->
            DirectChatSummary(
                roomId = UUID.fromString(rs.getString("room_id")),
                peerHandle = rs.getString("peer_handle"),
                peerAlias = rs.getString("display_name"),
                peerColor = rs.getString("profile_color"),
                lastMessage = rs.getString("content"),
                lastMessageAt = rs.getTimestamp("sent_at")?.toInstant(),
                unreadCount = rs.getInt("unread_count"),
            )
        },
        userId,
        userId,
        userId,
        userId,
        userId,
        userId,
        userId,
    )

    fun messages(userId: UUID, roomId: UUID, limit: Int): List<DirectChatMessage> {
        requireActiveMutual(userId, roomId)
        return jdbc.query(
            """
            select id,client_message_id,room_id,sender_id,content,sent_at
            from chat_messages where room_id=? order by sent_at desc limit ?
            """.trimIndent(),
            { rs, _ ->
                DirectChatMessage(
                    messageId = UUID.fromString(rs.getString("id")),
                    clientMessageId = rs.getString("client_message_id")?.let(UUID::fromString),
                    roomId = UUID.fromString(rs.getString("room_id")),
                    isMine = UUID.fromString(rs.getString("sender_id")) == userId,
                    content = rs.getString("content"),
                    sentAt = rs.getTimestamp("sent_at").toInstant(),
                )
            },
            roomId,
            limit.coerceIn(1, 100),
        ).reversed()
    }

    @Transactional
    fun send(userId: UUID, roomId: UUID, request: SendChatMessageRequest): DirectChatMessage {
        rateLimiter.enforce(userId, "CHAT_MESSAGE", 30, Duration.ofMinutes(1))
        requireActiveMutual(userId, roomId)
        val clientMessageId = runCatching { UUID.fromString(request.clientMessageId) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 clientMessageId가 필요합니다.")
        }
        val content = request.content.trim()
        if (content.isBlank() || content.length > 1000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1자부터 1000자까지 입력할 수 있습니다.")
        }
        jdbc.update(
            """
            insert into chat_messages(id,room_id,sender_id,client_message_id,content)
            values (?,?,?,?,?) on conflict(sender_id,client_message_id) do nothing
            """.trimIndent(),
            UUID.randomUUID(),
            roomId,
            userId,
            clientMessageId,
            content,
        )
        return jdbc.query(
            """
            select id,client_message_id,room_id,sender_id,content,sent_at
            from chat_messages where sender_id=? and client_message_id=?
            """.trimIndent(),
            { rs, _ ->
                DirectChatMessage(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("client_message_id")),
                    UUID.fromString(rs.getString("room_id")),
                    true,
                    rs.getString("content"),
                    rs.getTimestamp("sent_at").toInstant(),
                )
            },
            userId,
            clientMessageId,
        ).single()
    }

    private fun requireActiveMutual(userId: UUID, roomId: UUID) {
        val allowed = jdbc.queryForObject(
            """
            select exists(
              select 1 from direct_chat_pairs pair
              where pair.room_id=? and (?=pair.first_user_id or ?=pair.second_user_id)
                and exists(select 1 from user_follows follow
                  where follow.follower_id=pair.first_user_id and follow.followed_id=pair.second_user_id)
                and exists(select 1 from user_follows follow
                  where follow.follower_id=pair.second_user_id and follow.followed_id=pair.first_user_id)
                and not exists(select 1 from user_blocks block
                  where (block.blocker_id=pair.first_user_id and block.blocked_id=pair.second_user_id)
                     or (block.blocker_id=pair.second_user_id and block.blocked_id=pair.first_user_id))
            )
            """.trimIndent(),
            Boolean::class.java,
            roomId,
            userId,
            userId,
        ) == true
        if (!allowed) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "맞팔 상태인 차단되지 않은 사용자만 대화할 수 있습니다.")
        }
    }
}

@RestController
@RequestMapping("/api/v1/chat/rooms")
class ChatController(private val chat: ChatService) {
    @GetMapping
    fun rooms(principal: Principal) = chat.rooms(UUID.fromString(principal.name))

    @GetMapping("/{roomId}/messages")
    fun messages(
        principal: Principal,
        @PathVariable roomId: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = chat.messages(UUID.fromString(principal.name), roomId, limit)

    @PostMapping("/{roomId}/messages")
    fun send(
        principal: Principal,
        @PathVariable roomId: UUID,
        @RequestBody request: SendChatMessageRequest,
    ) = chat.send(UUID.fromString(principal.name), roomId, request)
}
