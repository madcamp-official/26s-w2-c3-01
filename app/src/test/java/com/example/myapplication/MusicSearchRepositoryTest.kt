package com.example.myapplication

import com.example.myapplication.data.remote.ITunesSearchResponse
import com.example.myapplication.data.remote.ITunesSongDto
import com.example.myapplication.data.remote.ITunesGenreDto
import com.example.myapplication.data.remote.DeezerArtistApi
import com.example.myapplication.data.remote.DeezerArtistDto
import com.example.myapplication.data.remote.DeezerArtistSearchResponse
import com.example.myapplication.data.remote.MusicSearchApi
import com.example.myapplication.data.remote.MusicSearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchRepositoryTest {
    @Test
    fun mapsITunesSongIntoProfileSearchResult() = runBlocking {
        val api = object : MusicSearchApi {
            override suspend fun genres(
                rootGenreId: Int,
                country: String,
                language: String,
            ): Map<String, ITunesGenreDto> = emptyMap()

            override suspend fun search(
                term: String,
                country: String,
                media: String,
                entity: String,
                limit: Int,
                explicit: String,
            ) = ITunesSearchResponse(
                resultCount = 1,
                results = listOf(
                    ITunesSongDto(
                        trackId = 123L,
                        artistId = 456L,
                        trackName = " 밤편지 ",
                        artistName = " 아이유 ",
                        collectionName = "Palette",
                        primaryGenreName = "K-Pop",
                        trackTimeMillis = 253_900L,
                        artworkUrl100 = "https://example.com/100x100bb.jpg",
                    ),
                ),
            )
        }

        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse(
                data = listOf(
                    DeezerArtistDto(
                        id = 1L,
                        name = "아이유",
                        picture_small = "https://cdn.example.com/iu-small.jpg",
                    ),
                ),
            )
        }

        val song = MusicSearchRepository(api, artistApi).search(" 아이유 ").single()

        assertEquals(123L, song.id)
        assertEquals("밤편지", song.title)
        assertEquals("아이유", song.artist)
        assertEquals(253, song.durationSeconds)
        assertTrue(song.artworkUrl.orEmpty().contains("600x600bb"))
        assertEquals("https://cdn.example.com/iu-small.jpg", song.artistImageUrl)
    }

    @Test
    fun mapsTopLevelITunesMusicGenres() = runBlocking {
        val api = object : MusicSearchApi {
            override suspend fun search(
                term: String,
                country: String,
                media: String,
                entity: String,
                limit: Int,
                explicit: String,
            ) = ITunesSearchResponse()

            override suspend fun genres(
                rootGenreId: Int,
                country: String,
                language: String,
            ) = mapOf(
                "34" to ITunesGenreDto(
                    name = "음악",
                    id = "34",
                    subgenres = mapOf(
                        "14" to ITunesGenreDto(name = "팝", id = "14"),
                        "21" to ITunesGenreDto(name = "록", id = "21"),
                    ),
                ),
            )
        }

        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse()
        }
        assertEquals(listOf("록", "팝"), MusicSearchRepository(api, artistApi).genres())
    }
}
