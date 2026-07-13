package com.example.myapplication.data.remote

import com.example.myapplication.core.model.MusicSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicSearchApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("country") country: String = "KR",
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 30,
        @Query("explicit") explicit: String = "No",
    ): ITunesSearchResponse

    @GET("WebObjects/MZStoreServices.woa/ws/genres")
    suspend fun genres(
        @Query("id") rootGenreId: Int = 34,
        @Query("cc") country: String = "kr",
        @Query("lang") language: String = "ko_kr",
    ): Map<String, ITunesGenreDto>
}

data class ITunesGenreDto(
    val name: String = "",
    val id: String = "",
    val subgenres: Map<String, ITunesGenreDto>? = emptyMap(),
)

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
    private val artistApi: DeezerArtistApi = ApiClient.createDeezerArtistApi(),
) {
    suspend fun search(keyword: String): List<MusicSearchResult> = coroutineScope {
        val term = keyword.trim()
        require(term.isNotEmpty()) { "검색어를 입력해 주세요." }
        val songs = async { api.search(term = term) }
        val deezerArtists = async {
            runCatching { artistApi.search(term).data }.getOrDefault(emptyList())
        }
        val artistImages = deezerArtists.await()
            .mapNotNull { artist ->
                artist.picture_small?.takeIf { it.startsWith("https://") }
                    ?.let { artist.name.normalizedMusicIdentity() to it }
            }
            .toMap()
        songs.await().results.toSearchResults(artistImages)
    }

    suspend fun trackArtwork(title: String, artist: String): String? {
        val safeTitle = title.trim()
        val safeArtist = artist.trim()
        if (safeTitle.isEmpty() || safeArtist.isEmpty()) return null
        val results = api.search(term = "$safeTitle $safeArtist", limit = 10)
            .results
            .toSearchResults()
        val normalizedTitle = safeTitle.normalizedMusicIdentity()
        val normalizedArtist = safeArtist.normalizedMusicIdentity()
        return results.firstOrNull {
            it.title.normalizedMusicIdentity() == normalizedTitle &&
                it.artist.normalizedMusicIdentity() == normalizedArtist
        }?.artworkUrl ?: results.firstOrNull {
            it.artist.normalizedMusicIdentity() == normalizedArtist
        }?.artworkUrl
    }

    private fun List<ITunesSongDto>.toSearchResults(
        artistImages: Map<String, String> = emptyMap(),
    ): List<MusicSearchResult> = mapNotNull { song ->
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
                artistImageUrl = artistImages[artist.normalizedMusicIdentity()],
            )
        }

    suspend fun genres(): List<String> = api.genres()
        .values
        .firstOrNull()
        ?.subgenres
        .orEmpty()
        .values
        .map(ITunesGenreDto::name)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
}

private fun String.highResolutionArtwork(): String =
    replace("100x100bb", "600x600bb").replace("100x100-75", "600x600-75")

private fun String.normalizedMusicIdentity(): String =
    trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
