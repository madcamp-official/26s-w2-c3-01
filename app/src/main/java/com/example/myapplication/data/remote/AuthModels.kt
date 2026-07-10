package com.example.myapplication.data.remote

data class LoginRequest(
    val email: String,
    val password: String,
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
)
