package com.example.myapplication.data.remote

import com.example.myapplication.core.model.MusicSearchResult
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicSearchApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("country") country: String = "KR",
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 10,
        @Query("explicit") explicit: String = "No",
    ): ITunesSearchResponse
}

data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesSongDto> = emptyList(),
)

data class ITunesSongDto(
    val trackId: Long? = null,
    val artistId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val primaryGenreName: String? = null,
    val releaseDate: String? = null,
    val trackTimeMillis: Long? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val trackViewUrl: String? = null,
)

class MusicSearchRepository(
    private val api: MusicSearchApi = ApiClient.createMusicSearchApi(),
) {
    suspend fun search(keyword: String): List<MusicSearchResult> {
        val term = keyword.trim()
        require(term.isNotEmpty()) { "검색어를 입력해 주세요." }
        return api.search(term = term).results.mapNotNull { song ->
            val id = song.trackId ?: return@mapNotNull null
            val title = song.trackName?.trim().orEmpty()
            val artist = song.artistName?.trim().orEmpty()
            if (title.isEmpty() || artist.isEmpty()) return@mapNotNull null
            MusicSearchResult(
                id = id,
                artistId = song.artistId,
                title = title,
                artist = artist,
                album = song.collectionName?.trim().orEmpty(),
                genre = song.primaryGenreName?.trim().orEmpty(),
                releaseDate = song.releaseDate,
                durationSeconds = ((song.trackTimeMillis ?: 0L) / 1_000L).toInt(),
                artworkUrl = song.artworkUrl100?.highResolutionArtwork(),
                previewUrl = song.previewUrl,
                appleMusicUrl = song.trackViewUrl,
            )
        }
    }
}

private fun String.highResolutionArtwork(): String =
    replace("100x100bb", "600x600bb").replace("100x100-75", "600x600-75")
