package com.example.myapplication.data.remote

import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.RelationshipStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class ChatRoomSummaryDto(
    val roomId: String,
    val peerHandle: String,
    val peerAlias: String,
    val peerColorHex: String,
    val lastMessage: String,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    val relationship: String
)

data class ChatMessageDto(
    val messageId: String,
    val clientMessageId: String? = null,
    val roomId: String,
    val senderId: String,
    val isMine: Boolean,
    val content: String,
    val sentAt: String
)

data class SendChatMessageRequestDto(
    val clientMessageId: String = UUID.randomUUID().toString(),
    val content: String
)

interface ChatApi {
    @GET("api/v1/chats")
    suspend fun listRooms(@Header("Authorization") authorization: String): List<ChatRoomSummaryDto>

    @POST("api/v1/chats/direct/{nearbyHandle}")
    suspend fun openDirectRoom(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String
    ): ChatRoomSummaryDto

    @GET("api/v1/chats/{roomId}/messages")
    suspend fun listMessages(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String
    ): List<ChatMessageDto>

    @POST("api/v1/chats/{roomId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
        @Body request: SendChatMessageRequestDto
    ): ChatMessageDto
}

class ChatRepository(
    private val api: ChatApi = ApiClient.createChatApi()
) {
    suspend fun rooms(token: String): Result<List<ChatPreview>> =
        runCatching { api.listRooms(token.bearer()).map { it.toDomain() } }

    suspend fun openDirectRoom(token: String, nearbyHandle: String): Result<ChatPreview> =
        runCatching { api.openDirectRoom(token.bearer(), nearbyHandle).toDomain() }

    suspend fun messages(token: String, roomId: String): Result<List<ChatMessage>> =
        runCatching { api.listMessages(token.bearer(), roomId).map { it.toDomain() } }

    suspend fun send(token: String, roomId: String, content: String): Result<ChatMessage> =
        runCatching {
            api.sendMessage(token.bearer(), roomId, SendChatMessageRequestDto(content = content)).toDomain()
        }

    private fun String.bearer(): String = "Bearer $this"
}

private fun ChatRoomSummaryDto.toDomain() = ChatPreview(
    roomId = roomId,
    peerHandle = peerHandle,
    peerAlias = peerAlias,
    peerColorHex = peerColorHex.toColorLong(),
    lastMessage = lastMessage,
    relativeTime = lastMessageAt.toRelativeLabel(),
    unreadCount = unreadCount,
    relationship = runCatching { RelationshipStatus.valueOf(relationship) }.getOrDefault(RelationshipStatus.NONE)
)

private fun ChatMessageDto.toDomain() = ChatMessage(
    messageId = messageId,
    clientMessageId = clientMessageId ?: messageId,
    roomId = roomId,
    isMine = isMine,
    content = content,
    sentAtLabel = sentAt.toTimeLabel(),
    deliveryState = if (isMine) DeliveryState.SENT else DeliveryState.READ
)

private fun String.toColorLong(): Long {
    val normalized = removePrefix("#")
    return runCatching { ("FF$normalized").takeLast(8).toLong(16) }.getOrDefault(0xFF25C76FL)
}

private fun String?.toRelativeLabel(): String {
    val timeMillis = this?.toEpochMillisOrNull() ?: return "방금"
    val seconds = ((System.currentTimeMillis() - timeMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "방금"
        seconds < 3600 -> "${seconds / 60}분 전"
        seconds < 86400 -> "${seconds / 3600}시간 전"
        else -> "${seconds / 86400}일 전"
    }
}

private fun String.toTimeLabel(): String =
    runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(toEpochMillisOrNull() ?: System.currentTimeMillis())
    }.getOrDefault("지금")

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(this)?.time
}.getOrNull() ?: runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(this)?.time
}.getOrNull()
