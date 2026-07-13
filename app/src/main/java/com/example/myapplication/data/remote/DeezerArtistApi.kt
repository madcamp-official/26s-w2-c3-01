package com.example.myapplication.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface DeezerArtistApi {
    @GET("search/artist")
    suspend fun search(@Query("q") artistName: String): DeezerArtistSearchResponse
}

data class DeezerArtistSearchResponse(
    val data: List<DeezerArtistDto> = emptyList(),
)

data class DeezerArtistDto(
    val id: Long? = null,
    val name: String = "",
    val picture_small: String? = null,
)
