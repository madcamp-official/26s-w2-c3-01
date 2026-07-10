package com.example.myapplication.data.remote

class AuthRepository(
    private val authApi: AuthApi = ApiClient.createAuthApi(),
) {
    suspend fun login(email: String, password: String): Result<TokenResponse> = runCatching {
        authApi.login(LoginRequest(email = email.trim(), password = password))
    }

    suspend fun googleLogin(idToken: String): Result<TokenResponse> = runCatching {
        authApi.googleLogin(GoogleLoginRequest(idToken))
    }
}
