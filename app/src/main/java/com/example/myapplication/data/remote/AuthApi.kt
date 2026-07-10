package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST(MelodyApiContract.Rest.LOGIN)
    suspend fun login(@Body request: LoginRequest): TokenResponse
}
