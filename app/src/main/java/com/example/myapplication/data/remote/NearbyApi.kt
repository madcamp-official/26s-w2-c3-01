package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class RemoteTrack(val title: String, val artist: String, val albumArtUrl: String?)
data class RemotePosition(val x: Float, val y: Float)
data class RemoteNearbyBubble(
    val nearbyHandle: String,
    val displayAlias: String,
    val profileColor: String,
    val displayPosition: RemotePosition,
    val matchScore: Int,
    val proximity: String,
    val relationship: String?,
    val canReact: Boolean?,
    val track: RemoteTrack?
)
data class RemoteNearbySnapshot(
    val generatedAt: String,
    val radiusMeters: Int?,
    val items: List<RemoteNearbyBubble>
)
data class LocationUpdateRequest(
    val requestId: String,
    val clientSessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)
data class MusicUpdateRequest(
    val title: String,
    val artist: String,
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val isPlaying: Boolean = true
)
data class RemotePresenceSettings(
    val discoverabilityScope: String,
    val musicVisibility: String,
    val discoveryRadiusMeters: Int,
    val allowReactions: Boolean
)
data class PresenceSettingsUpdateRequest(
    val discoverabilityScope: String,
    val musicVisibility: String,
    val discoveryRadiusMeters: Int,
    val allowReactions: Boolean
)

interface NearbyApi {
    @GET("api/v1/nearby/snapshot")
    suspend fun snapshot(@Header("Authorization") authorization: String): RemoteNearbySnapshot

    @POST("api/v1/nearby/location")
    suspend fun updateLocation(
        @Header("Authorization") authorization: String,
        @Body request: LocationUpdateRequest
    ): RemoteNearbySnapshot

    @POST("api/v1/nearby/music")
    suspend fun updateMusic(
        @Header("Authorization") authorization: String,
        @Body request: MusicUpdateRequest
    )

    @DELETE("api/v1/nearby/presence/{clientSessionId}")
    suspend fun stopPresence(
        @Header("Authorization") authorization: String,
        @Path("clientSessionId") clientSessionId: String
    )

    @GET("api/v1/me/presence-settings")
    suspend fun presenceSettings(
        @Header("Authorization") authorization: String
    ): RemotePresenceSettings

    @retrofit2.http.PUT("api/v1/me/presence-settings")
    suspend fun updatePresenceSettings(
        @Header("Authorization") authorization: String,
        @Body request: PresenceSettingsUpdateRequest
    ): RemotePresenceSettings
}
