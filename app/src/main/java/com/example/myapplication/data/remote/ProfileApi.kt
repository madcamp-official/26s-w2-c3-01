package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.PUT

data class RemoteProfile(
    val displayName: String,
    val profileColor: String,
    val bio: String?,
    val avatarDataUrl: String?,
    val genres: List<String>?,
    val moods: List<String>?,
    val discoverable: Boolean,
    val shareMusic: Boolean,
)
data class ProfileUpdateRequest(
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarDataUrl: String?,
    val genres: List<String>,
    val moods: List<String>,
)
data class PrivacyUpdateRequest(val discoverable: Boolean, val shareMusic: Boolean)

interface ProfileApi {
    @GET("api/v1/me") suspend fun me(@Header("Authorization") authorization: String): RemoteProfile
    @PATCH("api/v1/me") suspend fun update(
        @Header("Authorization") authorization: String,
        @Body request: ProfileUpdateRequest,
    ): RemoteProfile
    @PUT("api/v1/me/privacy") suspend fun privacy(
        @Header("Authorization") authorization: String,
        @Body request: PrivacyUpdateRequest,
    ): RemoteProfile
}
