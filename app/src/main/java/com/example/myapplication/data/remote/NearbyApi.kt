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
    val profileHandle: String? = null,
    val displayAlias: String,
    val avatarSeed: String? = null,
    val avatarUrl: String? = null,
    val profileColor: String,
    val displayPosition: RemotePosition,
    val matchScore: Int?,
    val tasteMatch: RemoteCommonTasteSummary? = null,
    val proximity: String,
    val distanceConfidence: String = "UNKNOWN",
    val distanceAccuracyMeters: Double? = null,
    val relationship: String?,
    val canReact: Boolean?,
    val track: RemoteTrack?,
)
data class RemoteNearbySnapshot(
    val generatedAt: String,
    val radiusMeters: Int?,
    val items: List<RemoteNearbyBubble>,
    val removedNearbyHandles: List<String>? = null,
)
data class RemotePopularTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val listenerCount: Int,
    val reactionCount: Int
)
data class LocationUpdateRequest(
    val requestId: String,
    val clientSessionId: String,
    val sequence: Long,
    val observedAtEpochMillis: Long,
    val source: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)
data class MusicUpdateRequest(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val isPlaying: Boolean = true,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionObservedAt: String? = null,
    val observedAt: String? = null,
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
data class NearbyReactionRequest(
    val clientReactionId: String,
    val reactionType: String,
    val trackTitle: String?,
    val trackArtist: String?
)
data class RemoteNearbyReaction(
    val reactionId: String,
    val clientReactionId: String,
    val reactionType: String,
    val createdAt: String
)
data class RemoteReceivedReaction(
    val reactionId: String,
    val clientReactionId: String,
    val reactionType: String,
    val senderAlias: String,
    val senderProfileHandle: String? = null,
    val senderAvatarUrl: String? = null,
    val trackTitle: String?,
    val trackArtist: String?,
    val createdAt: String
)
data class NearbyBeaconRequest(val clientSessionId: String)
data class RemoteNearbyBeacon(val beaconId: String, val expiresAt: String, val rotationAfterSeconds: Int)
data class ResolveNearbyBeaconsRequest(val beaconIds: List<String>)
data class RemoteResolvedNearbyBeacon(val beaconId: String, val user: RemoteNearbyBubble)
data class DirectProximityUpdateRequest(
    val beaconId: String,
    val proximity: String,
    val confidence: String,
    val method: String,
    val sequence: Long,
    val observedAtEpochMillis: Long,
)
data class DirectProximityBatchRequest(val updates: List<DirectProximityUpdateRequest>)
data class DirectProximityBatchResponse(val acceptedCount: Int, val receivedCount: Int)

interface NearbyApi {
    @POST("api/v1/nearby/beacons")
    suspend fun issueBeacon(
        @Header("Authorization") authorization: String,
        @Body request: NearbyBeaconRequest,
    ): RemoteNearbyBeacon

    @POST("api/v1/nearby/beacons/resolve")
    suspend fun resolveBeacons(
        @Header("Authorization") authorization: String,
        @Body request: ResolveNearbyBeaconsRequest,
    ): List<RemoteResolvedNearbyBeacon>

    @POST("api/v1/nearby/beacons/proximity")
    suspend fun reportDirectProximity(
        @Header("Authorization") authorization: String,
        @Body request: DirectProximityUpdateRequest,
    ): Boolean

    @POST("api/v1/nearby/beacons/proximity/batch")
    suspend fun reportDirectProximityBatch(
        @Header("Authorization") authorization: String,
        @Body request: DirectProximityBatchRequest,
    ): DirectProximityBatchResponse

    @GET("api/v1/nearby/snapshot")
    suspend fun snapshot(@Header("Authorization") authorization: String): RemoteNearbySnapshot

    @GET("api/v1/nearby/popular-tracks")
    suspend fun popularTracks(
        @Header("Authorization") authorization: String
    ): List<RemotePopularTrack>

    @GET("api/v1/nearby/reactions")
    suspend fun receivedReactions(
        @Header("Authorization") authorization: String
    ): List<RemoteReceivedReaction>

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

    @POST("api/v1/nearby/{nearbyHandle}/reactions")
    suspend fun sendReaction(
        @Header("Authorization") authorization: String,
        @Path("nearbyHandle") nearbyHandle: String,
        @Body request: NearbyReactionRequest
    ): RemoteNearbyReaction

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
