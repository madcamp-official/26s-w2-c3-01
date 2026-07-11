package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class RelationshipResponseDto(
    val targetHandle: String,
    val relationship: String,
    val chatRoomId: String? = null
)

data class ReportUserRequestDto(
    val reason: String = "SAFETY",
    val detail: String? = null
)

data class ReportUserResponseDto(
    val reportId: String,
    val status: String
)

interface SocialInteractionApi {
    @POST("api/v1/social/follows/{nearbyHandle}")
    suspend fun follow(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String
    ): RelationshipResponseDto

    @DELETE("api/v1/social/follows/{nearbyHandle}")
    suspend fun unfollow(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String
    ): RelationshipResponseDto

    @POST("api/v1/social/blocks/{nearbyHandle}")
    suspend fun block(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String
    ): RelationshipResponseDto

    @POST("api/v1/social/reports/{nearbyHandle}")
    suspend fun report(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String,
        @Body request: ReportUserRequestDto
    ): ReportUserResponseDto
}

class SocialInteractionRepository(
    private val api: SocialInteractionApi = ApiClient.createSocialInteractionApi()
) {
    suspend fun follow(token: String, nearbyHandle: String): Result<RelationshipResponseDto> =
        runCatching { api.follow(token.bearer(), nearbyHandle) }

    suspend fun unfollow(token: String, nearbyHandle: String): Result<RelationshipResponseDto> =
        runCatching { api.unfollow(token.bearer(), nearbyHandle) }

    suspend fun block(token: String, nearbyHandle: String): Result<RelationshipResponseDto> =
        runCatching { api.block(token.bearer(), nearbyHandle) }

    suspend fun report(token: String, nearbyHandle: String, reason: String = "SAFETY", detail: String? = null): Result<ReportUserResponseDto> =
        runCatching { api.report(token.bearer(), nearbyHandle, ReportUserRequestDto(reason, detail)) }

    private fun String.bearer(): String = "Bearer $this"
}
