package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST(MelodyApiContract.Rest.LOGIN)
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("/api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): TokenResponse
}
