package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT

data class BuildingLoungeSummaryDto(
    val id: String,
    val buildingId: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val category: String,
    val distanceMeters: Double,
    val inside: Boolean,
    val activeMembers: Int,
    val subLoungeCount: Int
)

data class EnterBuildingLoungeRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val wifiFingerprint: String
)

data class BuildingLoungeSessionResponseDto(
    val lounge: BuildingLoungeSummaryDto,
    val entered: Boolean
)

data class HeartbeatRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val wifiFingerprint: String
)

data class HeartbeatResponseDto(
    val inside: Boolean,
    val outsideCount: Int,
    val forcedExit: Boolean
)

data class CreateSubLoungeRequestDto(
    val title: String,
    val style: String? = null
)

data class SubLoungeSummaryDto(
    val id: String,
    val buildingLoungeId: String,
    val title: String,
    val style: String?,
    val memberCount: Int,
    val createdAt: String
)

data class LoungeListeningStatusDto(
    val listenerAlias: String,
    val trackTitle: String?,
    val artistName: String?,
    val albumArtUrl: String?,
    val isPlaying: Boolean,
    val updatedAt: String,
    val listenerProfileHandle: String? = null,
)

data class LoungeRecommendationCardDto(
    val id: String,
    val subLoungeId: String,
    val clientCardId: String,
    val senderAlias: String,
    val trackTitle: String,
    val artistName: String,
    val message: String?,
    val reactionCount: Int,
    val reactedByMe: Boolean,
    val canDelete: Boolean = false,
    val createdAt: String,
    val senderProfileHandle: String? = null,
)

data class LoungeMemberProfileDto(
    val profileHandle: String,
    val displayName: String,
    val profileColor: String,
)

data class LoungeMusicSearchResultDto(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val storeUrl: String?,
)

data class LoungePollOptionDto(val key: String, val voteCount: Int)
data class LoungePollStateDto(val options: List<LoungePollOptionDto>, val myVote: String?)

data class SubLoungeSnapshotDto(
    val id: String,
    val buildingLoungeId: String,
    val title: String,
    val style: String?,
    val memberCount: Int,
    val joined: Boolean,
    val canDelete: Boolean = false,
    val listeningStatuses: List<LoungeListeningStatusDto>,
    val cards: List<LoungeRecommendationCardDto>,
    val poll: LoungePollStateDto,
    val generatedAt: String,
    // Nullable for rolling deployments: Gson assigns null when an older server omits this field.
    val members: List<LoungeMemberProfileDto>? = emptyList(),
)

data class UpdateLoungeListeningRequestDto(
    val trackTitle: String = "",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = true
)

data class CreateLoungeCardRequestDto(
    val clientCardId: String,
    val trackTitle: String,
    val artistName: String,
    val message: String? = null
)

data class LoungeReactionRequestDto(val reactionType: String)
data class LoungeVoteRequestDto(val targetKey: String)

interface BuildingLoungeApi {
    @GET("api/v1/building-lounges/sub-lounges/active")
    suspend fun activeSubLounge(
        @Header("Authorization") authorization: String
    ): SubLoungeSnapshotDto?

    @GET("api/v1/building-lounges/nearby")
    suspend fun nearby(
        @Header("Authorization") authorization: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("wifiFingerprint") wifiFingerprint: String,
        @Query("wifiName") wifiName: String
    ): List<BuildingLoungeSummaryDto>

    @POST("api/v1/building-lounges/{loungeId}/enter")
    suspend fun enter(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String,
        @Body request: EnterBuildingLoungeRequestDto
    ): BuildingLoungeSessionResponseDto

    @POST("api/v1/building-lounges/{loungeId}/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String,
        @Body request: HeartbeatRequestDto
    ): HeartbeatResponseDto

    @POST("api/v1/building-lounges/{loungeId}/leave")
    suspend fun leave(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String
    )

    @GET("api/v1/building-lounges/{loungeId}/sub-lounges")
    suspend fun subLounges(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String
    ): List<SubLoungeSummaryDto>

    @POST("api/v1/building-lounges/{loungeId}/sub-lounges")
    suspend fun createSubLounge(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String,
        @Body request: CreateSubLoungeRequestDto
    ): SubLoungeSummaryDto

    @GET("api/v1/building-lounges/sub-lounges/{subLoungeId}")
    suspend fun subLoungeSnapshot(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String
    ): SubLoungeSnapshotDto

    @POST("api/v1/building-lounges/sub-lounges/{subLoungeId}/join")
    suspend fun joinSubLounge(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String
    ): SubLoungeSummaryDto

    @POST("api/v1/building-lounges/sub-lounges/{subLoungeId}/leave")
    suspend fun leaveSubLounge(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String
    )

    @PUT("api/v1/building-lounges/sub-lounges/{subLoungeId}/listening")
    suspend fun updateListening(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String,
        @Body request: UpdateLoungeListeningRequestDto
    )

    @POST("api/v1/building-lounges/sub-lounges/{subLoungeId}/cards")
    suspend fun addCard(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String,
        @Body request: CreateLoungeCardRequestDto
    ): LoungeRecommendationCardDto

    @POST("api/v1/building-lounges/cards/{cardId}/reactions")
    suspend fun reactToCard(
        @Header("Authorization") authorization: String,
        @Path("cardId") cardId: String,
        @Body request: LoungeReactionRequestDto
    ): LoungeRecommendationCardDto

    @DELETE("api/v1/building-lounges/sub-lounges/cards/{cardId}")
    suspend fun deleteCard(
        @Header("Authorization") authorization: String,
        @Path("cardId") cardId: String,
    )

    @DELETE("api/v1/building-lounges/sub-lounges/{subLoungeId}")
    suspend fun deleteSubLounge(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String,
    )

    @GET("api/v1/music/search")
    suspend fun searchMusic(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
    ): List<LoungeMusicSearchResultDto>

    @PUT("api/v1/building-lounges/sub-lounges/{subLoungeId}/vote")
    suspend fun vote(
        @Header("Authorization") authorization: String,
        @Path("subLoungeId") subLoungeId: String,
        @Body request: LoungeVoteRequestDto
    ): LoungePollStateDto
}

class BuildingLoungeRepository(
    private val api: BuildingLoungeApi = ApiClient.createBuildingLoungeApi()
) {
    suspend fun nearby(
        token: String,
        latitude: Double,
        longitude: Double,
        wifiFingerprint: String,
        wifiName: String,
    ): Result<List<BuildingLoungeSummaryDto>> =
        runCatching { api.nearby(token.bearer(), latitude, longitude, wifiFingerprint, wifiName) }

    suspend fun activeSubLounge(token: String): Result<SubLoungeSnapshotDto?> =
        runCatching { api.activeSubLounge(token.bearer()) }

    suspend fun enter(
        token: String,
        loungeId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        wifiFingerprint: String
    ): Result<BuildingLoungeSessionResponseDto> =
        runCatching {
            api.enter(token.bearer(), loungeId, EnterBuildingLoungeRequestDto(latitude, longitude, accuracyMeters, wifiFingerprint))
        }

    suspend fun heartbeat(
        token: String,
        loungeId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        wifiFingerprint: String
    ): Result<HeartbeatResponseDto> =
        runCatching {
            api.heartbeat(token.bearer(), loungeId, HeartbeatRequestDto(latitude, longitude, accuracyMeters, wifiFingerprint))
        }

    suspend fun leave(token: String, loungeId: String): Result<Unit> =
        runCatching { api.leave(token.bearer(), loungeId) }

    suspend fun subLounges(token: String, loungeId: String): Result<List<SubLoungeSummaryDto>> =
        runCatching { api.subLounges(token.bearer(), loungeId) }

    suspend fun createSubLounge(token: String, loungeId: String, title: String, style: String?): Result<SubLoungeSummaryDto> =
        runCatching { api.createSubLounge(token.bearer(), loungeId, CreateSubLoungeRequestDto(title, style)) }

    suspend fun snapshot(token: String, subLoungeId: String): Result<SubLoungeSnapshotDto> =
        runCatching { api.subLoungeSnapshot(token.bearer(), subLoungeId) }

    suspend fun joinSubLounge(token: String, subLoungeId: String): Result<SubLoungeSummaryDto> =
        runCatching { api.joinSubLounge(token.bearer(), subLoungeId) }

    suspend fun leaveSubLounge(token: String, subLoungeId: String): Result<Unit> =
        runCatching { api.leaveSubLounge(token.bearer(), subLoungeId) }

    suspend fun deleteSubLounge(token: String, subLoungeId: String): Result<Unit> =
        runCatching { api.deleteSubLounge(token.bearer(), subLoungeId) }

    suspend fun updateListening(token: String, subLoungeId: String, request: UpdateLoungeListeningRequestDto): Result<Unit> =
        runCatching { api.updateListening(token.bearer(), subLoungeId, request) }

    suspend fun addCard(token: String, subLoungeId: String, request: CreateLoungeCardRequestDto): Result<LoungeRecommendationCardDto> =
        runCatching { api.addCard(token.bearer(), subLoungeId, request) }

    suspend fun reactToCard(token: String, cardId: String, reactionType: String): Result<LoungeRecommendationCardDto> =
        runCatching { api.reactToCard(token.bearer(), cardId, LoungeReactionRequestDto(reactionType)) }

    suspend fun deleteCard(token: String, cardId: String): Result<Unit> =
        runCatching { api.deleteCard(token.bearer(), cardId) }

    suspend fun searchMusic(token: String, query: String): Result<List<LoungeMusicSearchResultDto>> =
        runCatching { api.searchMusic(token.bearer(), query) }

    suspend fun vote(token: String, subLoungeId: String, targetKey: String): Result<LoungePollStateDto> =
        runCatching { api.vote(token.bearer(), subLoungeId, LoungeVoteRequestDto(targetKey)) }

    private fun String.bearer(): String = "Bearer $this"
}
