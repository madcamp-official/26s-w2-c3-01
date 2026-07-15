package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

data class ListenEventRequest(
    val clientEventId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val provider: String? = null,
    val providerTrackId: String? = null,
    val sourceType: String = "ANDROID_MEDIA_SESSION",
    val sourcePackage: String? = null,
    val startedAt: String,
    val endedAt: String,
    val playedMs: Long,
    val durationMs: Long? = null,
    val completionRatio: Double? = null,
)

data class ListenEventBatchRequest(val events: List<ListenEventRequest>)
data class ListenEventBatchResponse(
    val collectionEnabled: Boolean,
    val acceptedCount: Int,
    val duplicateCount: Int,
)

data class TasteFeedbackRequest(
    val clientFeedbackId: String,
    val targetProfileHandle: String,
    val action: String,
)

interface TasteApi {
    @POST("api/v1/taste/listens/batch")
    suspend fun uploadListenEvents(
        @Header("Authorization") authorization: String,
        @Body request: ListenEventBatchRequest,
    ): ListenEventBatchResponse

    @DELETE("api/v1/taste/listens")
    suspend fun clearListenEvents(
        @Header("Authorization") authorization: String,
    )

    @POST("api/v1/taste/feedback")
    suspend fun recordFeedback(
        @Header("Authorization") authorization: String,
        @Body request: TasteFeedbackRequest,
    )
}
