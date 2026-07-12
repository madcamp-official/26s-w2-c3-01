package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.Query

interface AuthApi {
    @GET("/api/v1/auth/email-availability")
    suspend fun emailAvailability(@Query("email") email: String): EmailAvailabilityResponse

    @POST(MelodyApiContract.Rest.LOGIN)
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("/api/v1/auth/signup")
    suspend fun signup(@Body request: SignupRequest): TokenResponse

    @POST("/api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): TokenResponse

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String, @Body request: LogoutRequest)

    @PUT("/api/v1/auth/onboarding")
    suspend fun completeOnboarding(
        @Header("Authorization") authorization: String,
        @Body request: OnboardingRequest,
    )

    @DELETE("/api/v1/auth/account")
    suspend fun deleteAccount(@Header("Authorization") authorization: String)
}
