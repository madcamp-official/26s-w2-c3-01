package com.example.myapplication.data.remote

data class LoginRequest(
    val email: String,
    val password: String,
)
data class SignupRequest(
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val displayName: String,
)
data class EmailAvailabilityResponse(val email: String, val available: Boolean)

data class GoogleLoginRequest(val idToken: String)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String?)
data class OnboardingRequest(
    val acceptedTerms: Boolean,
    val termsVersion: String,
    val genres: List<String>,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val isNewUser: Boolean = false,
    val onboardingComplete: Boolean = false,
)
