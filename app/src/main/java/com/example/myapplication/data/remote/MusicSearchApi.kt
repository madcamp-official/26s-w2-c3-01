package com.example.myapplication.data.remote

import com.example.myapplication.core.model.MusicSearchResult
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val artistImageCache = ConcurrentHashMap<String, String>()
    private val trackMediaCache = ConcurrentHashMap<String, MusicSearchResult>()
    private val artistImageLookupLimiter = Semaphore(6)

    /** Fetches only the data required to start playback without waiting for artist image enrichment. */
    suspend fun searchPreviews(keyword: String): List<MusicSearchResult> {
        val term = keyword.trim()
        require(term.isNotEmpty()) { "검색어를 입력해 주세요." }
        return api.search(term = term).results.toSearchResults()
    }

    /** Resolves one track's preview and artwork without involving Deezer artist enrichment. */
    suspend fun findTrackMedia(title: String, artist: String): MusicSearchResult? {
        val safeTitle = title.trim()
        val safeArtist = artist.withoutMusicQualifier().trim()
        if (safeTitle.isEmpty() || safeArtist.isEmpty()) return null
        val cacheKey = "${safeTitle.normalizedMusicIdentity()}\u0000${safeArtist.normalizedMusicIdentity()}"
        trackMediaCache[cacheKey]?.let { return it }

        val combinedResults = api.search(term = "$safeTitle $safeArtist", limit = 30)
            .results
            .toSearchResults()
        val normalizedTitle = safeTitle.normalizedMusicIdentity()
        val normalizedArtist = safeArtist.normalizedMusicIdentity()
        val exactTitleCandidates = combinedResults.filter {
            it.title.normalizedMusicIdentity() == normalizedTitle
        }
        val directMatch = exactTitleCandidates.firstOrNull {
            it.artist.withoutMusicQualifier().normalizedMusicIdentity() == normalizedArtist
        }
        val resolved = directMatch ?: run {
            // The Korean storefront localizes some artist names (ILLIT -> 아일릿). Resolve the
            // storefront artist id separately, then use it to disambiguate identical song titles.
            val artistResults = api.search(term = safeArtist, limit = 30)
                .results
                .toSearchResults()
            val localizedArtistId = artistResults.firstOrNull {
                it.artist.withoutMusicQualifier().normalizedMusicIdentity() == normalizedArtist
            }?.artistId ?: artistResults.firstOrNull()?.artistId
            exactTitleCandidates.firstOrNull {
                localizedArtistId != null && it.artistId == localizedArtistId
            } ?: exactTitleCandidates.firstOrNull()
                ?.takeIf { combinedResults.firstOrNull() === it }
        }
        return resolved?.also { trackMediaCache[cacheKey] = it }
    }

    suspend fun search(keyword: String): List<MusicSearchResult> = coroutineScope {
        val term = keyword.trim()
        require(term.isNotEmpty()) { "검색어를 입력해 주세요." }
        val songs = async { api.search(term = term) }
        val deezerArtists = async {
            runCatching { artistApi.search(term).data }.getOrDefault(emptyList())
        }
        val artistImages = deezerArtists.await().toArtistImageMap()
        artistImageCache.putAll(artistImages)
        val searchResults = songs.await().results.toSearchResults(artistImages)
        val missingArtistImages = searchResults
            .filter { it.artistImageUrl.isNullOrBlank() }
            .map(MusicSearchResult::artist)
            .distinct()
            .map { artistName ->
                async {
                    artistName.normalizedMusicIdentity() to runCatching {
                        artistImage(artistName)
                    }.getOrNull()
                }
            }
            .awaitAll()
            .mapNotNull { (artistName, imageUrl) -> imageUrl?.let { artistName to it } }
            .toMap()
        searchResults.map { result ->
            result.copy(
                artistImageUrl = result.artistImageUrl
                    ?: missingArtistImages[result.artist.normalizedMusicIdentity()],
            )
        }
    }

    suspend fun artistImage(artistName: String): String? {
        val term = artistName.trim()
        val normalizedName = term.normalizedMusicIdentity()
        if (normalizedName.isEmpty()) return null
        artistImageCache[normalizedName]?.let { return it }

        return artistImageLookupLimiter.withPermit {
            artistImageCache[normalizedName]?.let { return@withPermit it }
            val artists = artistApi.search(term).data
            val imageUrl = artists.toArtistImageMap()[normalizedName]
                ?: artists.firstNotNullOfOrNull { artist ->
                    artist.picture_small?.takeIf(String::isUsableDeezerArtistImage)
                }
                ?: return@withPermit null
            artistImageCache[normalizedName] = imageUrl
            imageUrl
        }
    }

    suspend fun trackArtwork(title: String, artist: String): String? {
        return findTrackMedia(title, artist)?.artworkUrl
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

    suspend fun genres(): List<String> = SUPPORTED_PROFILE_GENRES
}

internal val SUPPORTED_PROFILE_GENRES = listOf(
    "발라드",
    "댄스",
    "랩/힙합",
    "R&B/Soul",
    "인디음악",
    "트로트",
    "포크/블루스",
    "POP",
    "록/메탈",
    "일렉트로니카",
    "포크/블루스/컨트리",
)

private fun String.highResolutionArtwork(): String =
    replace("100x100bb", "600x600bb").replace("100x100-75", "600x600-75")

private fun String.normalizedMusicIdentity(): String =
    trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun String.withoutMusicQualifier(): String =
    replace(Regex("\\([^)]*\\)|（[^）]*）"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun List<DeezerArtistDto>.toArtistImageMap(): Map<String, String> = buildMap {
    this@toArtistImageMap.forEach { artist ->
        val normalizedName = artist.name.normalizedMusicIdentity()
        val imageUrl = artist.picture_small?.takeIf(String::isUsableDeezerArtistImage)
        if (normalizedName.isNotEmpty() && imageUrl != null && normalizedName !in this) {
            put(normalizedName, imageUrl)
        }
    }
}

private fun String.isUsableDeezerArtistImage(): Boolean =
    startsWith("https://") && !contains("d41d8cd98f00b204e9800998ecf8427e", ignoreCase = true)
