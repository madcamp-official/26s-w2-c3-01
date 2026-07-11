package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class LyriaAliasGenerateRequest(
    val moods: Map<String, Int>,
    val genre: String,
    val instruments: List<String>,
    val pitch: Int,
    val speed: Int
)

data class LyriaMusicResponse(
    val audioBase64: String,
    val mimeType: String,
    val description: String?,
    val model: String,
    val durationSeconds: Int
)

interface LyriaMusicApi {
    @POST("/api/v1/lyria/generate")
    suspend fun generate(
        @Header("Authorization") authorization: String,
        @Body request: LyriaAliasGenerateRequest
    ): LyriaMusicResponse
}

class LyriaMusicRepository(
    private val api: LyriaMusicApi = ApiClient.createLyriaMusicApi()
) {
    suspend fun generate(token: String, request: LyriaAliasGenerateRequest): Result<LyriaMusicResponse> =
        runCatching { api.generate("Bearer $token", request) }
}
