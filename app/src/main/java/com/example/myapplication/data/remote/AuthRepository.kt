package com.example.myapplication.data.remote

class AuthRepository(
    private val authApi: AuthApi = ApiClient.createAuthApi(),
) {
    suspend fun login(email: String, password: String): Result<TokenResponse> = runCatching {
        authApi.login(LoginRequest(email = email.trim(), password = password))
    }
    suspend fun emailAvailability(email: String): Result<EmailAvailabilityResponse> = runCatching {
        authApi.emailAvailability(email.trim().lowercase())
    }

    suspend fun signup(
        email: String,
        password: String,
        passwordConfirmation: String,
        displayName: String,
    ): Result<TokenResponse> = runCatching {
        authApi.signup(SignupRequest(email.trim().lowercase(), password, passwordConfirmation, displayName.trim()))
    }

    suspend fun googleLogin(idToken: String): Result<TokenResponse> = runCatching {
        authApi.googleLogin(GoogleLoginRequest(idToken))
    }

    suspend fun refresh(refreshToken: String): Result<TokenResponse> = runCatching {
        authApi.refresh(RefreshRequest(refreshToken))
    }

    suspend fun logout(accessToken: String, refreshToken: String?): Result<Unit> = runCatching {
        authApi.logout("Bearer $accessToken", LogoutRequest(refreshToken))
    }

    suspend fun completeOnboarding(
        accessToken: String,
        genres: List<String>,
        moods: List<String>,
    ): Result<Unit> = runCatching {
        authApi.completeOnboarding(
            "Bearer $accessToken",
            OnboardingRequest(true, "2026-07-11", genres, moods),
        )
    }

    suspend fun deleteAccount(accessToken: String): Result<Unit> = runCatching {
        authApi.deleteAccount("Bearer $accessToken")
    }
}
