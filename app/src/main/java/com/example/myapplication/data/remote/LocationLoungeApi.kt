package com.example.myapplication.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
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

interface LocationLoungeApi {
    @GET("api/v1/location-lounges")
    suspend fun snapshot(
        @Header("Authorization") authorization: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
    ): List<LocationLoungeSummaryDto>

    @POST("api/v1/location-lounges")
    suspend fun create(@Header("Authorization") authorization: String): LocationLoungeSummaryDto
}

class LocationLoungeRepository(
    private val api: LocationLoungeApi = ApiClient.createLocationLoungeApi(),
) {
    suspend fun snapshot(token: String, latitude: Double, longitude: Double): Result<List<BuildingLoungeSummaryDto>> =
        runCatching { api.snapshot("Bearer $token", latitude, longitude).map { it.asMapSummary(latitude, longitude) } }

    suspend fun create(token: String): Result<LocationLoungeSummaryDto> =
        runCatching { api.create("Bearer $token") }

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

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
