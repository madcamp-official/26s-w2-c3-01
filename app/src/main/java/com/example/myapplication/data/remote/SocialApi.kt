package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class RemoteFollowResponse(
    val following: Boolean,
    val mutual: Boolean,
    val relationship: String,
    val roomId: String?,
    val peerAlias: String,
    val peerColor: String
)

data class RemoteBlockedUser(
    val blockId: String,
    val displayAlias: String,
    val profileColor: String,
    val blockedAt: String
)

data class ReportSubmitRequest(val requestId: String, val reason: String, val description: String?)
data class RemoteReportResponse(val reportId: String, val status: String, val submittedAt: String)

data class RemoteChatSummary(
    val roomId: String,
    val peerHandle: String?,
    val peerAlias: String,
    val peerColor: String,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val unreadCount: Int,
    val relationship: String
)

data class RemoteChatMessage(
    val messageId: String,
    val clientMessageId: String?,
    val roomId: String,
    val isMine: Boolean,
    val content: String,
    val sentAt: String
)

data class SendChatMessageRequest(val clientMessageId: String, val content: String)

interface SocialApi {
    @PUT("api/v1/nearby/{handle}/follow")
    suspend fun follow(
        @Header("Authorization") authorization: String,
        @Path("handle") handle: String
    ): RemoteFollowResponse

    @DELETE("api/v1/nearby/{handle}/follow")
    suspend fun unfollow(
        @Header("Authorization") authorization: String,
        @Path("handle") handle: String
    ): RemoteFollowResponse

    @PUT("api/v1/nearby/{handle}/block")
    suspend fun block(
        @Header("Authorization") authorization: String,
        @Path("handle") handle: String
    ): RemoteBlockedUser

    @GET("api/v1/me/blocks")
    suspend fun blocks(@Header("Authorization") authorization: String): List<RemoteBlockedUser>

    @DELETE("api/v1/me/blocks/{blockId}")
    suspend fun unblock(
        @Header("Authorization") authorization: String,
        @Path("blockId") blockId: String
    )

    @POST("api/v1/nearby/{handle}/reports")
    suspend fun report(
        @Header("Authorization") authorization: String,
        @Path("handle") handle: String,
        @Body request: ReportSubmitRequest
    ): RemoteReportResponse

    @GET("api/v1/chat/rooms")
    suspend fun chatRooms(@Header("Authorization") authorization: String): List<RemoteChatSummary>

    @GET("api/v1/chat/rooms/{roomId}/messages")
    suspend fun chatMessages(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String
    ): List<RemoteChatMessage>

    @POST("api/v1/chat/rooms/{roomId}/messages")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
        @Body request: SendChatMessageRequest
    ): RemoteChatMessage
}
