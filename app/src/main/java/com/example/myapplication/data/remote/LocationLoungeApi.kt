package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationLoungeSummaryDto(
    val loungeId: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radius: Int,
    val currentUserCount: Int,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val status: String,
    val available: Boolean,
)

data class LocationLoungeChatRoomDto(
    val chatRoomId: String,
    val loungeId: String,
    val ownerId: String,
    val title: String,
    val joined: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val status: String,
    val memberCount: Int = 0,
)

data class CreateLocationLoungeChatRoomRequestDto(val title: String)

data class ActiveLocationLoungeRoomDto(
    val room: LocationLoungeChatRoomDto,
    val cards: List<LoungeRecommendationCardDto>,
)

data class LocationLoungeEntry(
    val lounge: BuildingLoungeSummaryDto,
    val rooms: List<SubLoungeSummaryDto>,
)

interface LocationLoungeApi {
    @GET("api/v1/location-lounges")
    suspend fun snapshot(
        @Header("Authorization") authorization: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
    ): List<LocationLoungeSummaryDto>

    @POST("api/v1/location-lounges")
    suspend fun create(@Header("Authorization") authorization: String): LocationLoungeSummaryDto

    @GET("api/v1/location-lounges/{loungeId}/chat-rooms")
    suspend fun chatRooms(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String,
    ): List<LocationLoungeChatRoomDto>

    @GET("api/v1/location-lounges/chat-rooms/active")
    suspend fun activeChatRoom(
        @Header("Authorization") authorization: String,
    ): ActiveLocationLoungeRoomDto?

    @POST("api/v1/location-lounges/{loungeId}/chat-rooms")
    suspend fun createChatRoom(
        @Header("Authorization") authorization: String,
        @Path("loungeId") loungeId: String,
        @Body request: CreateLocationLoungeChatRoomRequestDto,
    ): LocationLoungeChatRoomDto

    @POST("api/v1/location-lounges/chat-rooms/{roomId}/join")
    suspend fun joinChatRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
    ): LocationLoungeChatRoomDto

    @POST("api/v1/location-lounges/chat-rooms/{roomId}/leave")
    suspend fun leaveChatRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
    )

    @DELETE("api/v1/location-lounges/chat-rooms/{roomId}")
    suspend fun deleteChatRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
    )

    @GET("api/v1/location-lounges/chat-rooms/{roomId}/cards")
    suspend fun cards(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
    ): List<LoungeRecommendationCardDto>

    @POST("api/v1/location-lounges/chat-rooms/{roomId}/cards")
    suspend fun addCard(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
        @Body request: CreateLoungeCardRequestDto,
    ): LoungeRecommendationCardDto

    @POST("api/v1/location-lounges/cards/{cardId}/reactions")
    suspend fun reactToCard(
        @Header("Authorization") authorization: String,
        @Path("cardId") cardId: String,
        @Body request: LoungeReactionRequestDto,
    ): LoungeRecommendationCardDto

    @DELETE("api/v1/location-lounges/cards/{cardId}")
    suspend fun deleteCard(
        @Header("Authorization") authorization: String,
        @Path("cardId") cardId: String,
    )
}

class LocationLoungeRepository(
    private val api: LocationLoungeApi = ApiClient.createLocationLoungeApi(),
) {
    suspend fun snapshot(token: String, latitude: Double, longitude: Double): Result<List<BuildingLoungeSummaryDto>> =
        runCatching { api.snapshot("Bearer $token", latitude, longitude).map { it.asMapSummary(latitude, longitude) } }

    suspend fun create(token: String): Result<LocationLoungeSummaryDto> =
        runCatching { api.create("Bearer $token") }

    suspend fun enter(
        token: String,
        loungeId: String,
        latitude: Double,
        longitude: Double,
    ): Result<LocationLoungeEntry> = runCatching {
        val authorization = "Bearer $token"
        val lounge = api.snapshot(authorization, latitude, longitude)
            .firstOrNull { it.loungeId == loungeId && it.status == "ACTIVE" }
            ?: error("Location lounge not found")
        check(lounge.available) { "Outside location lounge" }
        LocationLoungeEntry(
            lounge = lounge.asMapSummary(latitude, longitude),
            rooms = api.chatRooms(authorization, loungeId).map { it.asSubLoungeSummary() },
        )
    }

    suspend fun isInside(
        token: String,
        loungeId: String,
        latitude: Double,
        longitude: Double,
    ): Result<Boolean> = runCatching {
        api.snapshot("Bearer $token", latitude, longitude)
            .firstOrNull { it.loungeId == loungeId && it.status == "ACTIVE" }
            ?.available == true
    }

    suspend fun chatRooms(token: String, loungeId: String): Result<List<SubLoungeSummaryDto>> =
        runCatching { api.chatRooms("Bearer $token", loungeId).map { it.asSubLoungeSummary() } }

    suspend fun activeChatRoom(token: String): Result<SubLoungeSnapshotDto?> =
        runCatching {
            api.activeChatRoom("Bearer $token")?.let { active ->
                active.room.asSubLoungeSnapshot(active.cards)
            }
        }

    suspend fun createChatRoom(token: String, loungeId: String, title: String): Result<SubLoungeSummaryDto> =
        runCatching {
            api.createChatRoom(
                "Bearer $token",
                loungeId,
                CreateLocationLoungeChatRoomRequestDto(title),
            ).asSubLoungeSummary()
        }

    suspend fun joinChatRoom(token: String, roomId: String): Result<SubLoungeSnapshotDto> =
        runCatching {
            val authorization = "Bearer $token"
            val room = api.joinChatRoom(authorization, roomId)
            room.asSubLoungeSnapshot(api.cards(authorization, roomId))
        }

    suspend fun roomSnapshot(token: String, loungeId: String, roomId: String): Result<SubLoungeSnapshotDto> =
        runCatching {
            val authorization = "Bearer $token"
            val room = api.chatRooms(authorization, loungeId)
                .first { it.chatRoomId == roomId }
            room.asSubLoungeSnapshot(api.cards(authorization, roomId))
        }

    suspend fun addCard(
        token: String,
        roomId: String,
        request: CreateLoungeCardRequestDto,
    ): Result<LoungeRecommendationCardDto> =
        runCatching { api.addCard("Bearer $token", roomId, request) }

    suspend fun reactToCard(
        token: String,
        cardId: String,
        reactionType: String,
    ): Result<LoungeRecommendationCardDto> =
        runCatching {
            api.reactToCard("Bearer $token", cardId, LoungeReactionRequestDto(reactionType))
        }

    suspend fun deleteCard(token: String, cardId: String): Result<Unit> =
        runCatching { api.deleteCard("Bearer $token", cardId) }

    suspend fun leaveChatRoom(token: String, roomId: String): Result<Unit> =
        runCatching { api.leaveChatRoom("Bearer $token", roomId) }

    suspend fun deleteChatRoom(token: String, roomId: String): Result<Unit> =
        runCatching { api.deleteChatRoom("Bearer $token", roomId) }

    private fun LocationLoungeSummaryDto.asMapSummary(userLatitude: Double, userLongitude: Double) =
        BuildingLoungeSummaryDto(
            id = loungeId,
            buildingId = loungeId,
            name = "위치 라운지 · ${currentUserCount}명",
            address = null,
            latitude = centerLatitude,
            longitude = centerLongitude,
            radiusMeters = radius,
            category = "LOCATION",
            distanceMeters = distanceMeters(userLatitude, userLongitude, centerLatitude, centerLongitude),
            inside = available,
            activeMembers = currentUserCount,
            subLoungeCount = 0,
        )

    private fun LocationLoungeChatRoomDto.asSubLoungeSummary() = SubLoungeSummaryDto(
        id = chatRoomId,
        buildingLoungeId = loungeId,
        title = title,
        style = null,
        memberCount = memberCount,
        createdAt = createdAt,
    )

    private fun LocationLoungeChatRoomDto.asSubLoungeSnapshot(
        recommendationCards: List<LoungeRecommendationCardDto> = emptyList(),
    ) = SubLoungeSnapshotDto(
        id = chatRoomId,
        buildingLoungeId = loungeId,
        title = title,
        style = null,
        memberCount = memberCount,
        joined = joined,
        canDelete = false,
        listeningStatuses = emptyList(),
        cards = recommendationCards,
        poll = LoungePollStateDto(emptyList(), null),
        generatedAt = updatedAt,
        members = emptyList(),
    )

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
