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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchRepositoryTest {
    @Test
    fun resolvesLocalizedTitleOnlyThroughExactCrossStoreMetadata() = runBlocking {
        val officialPreview = "https://audio.example/day6-official.m4a"
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
            ): ITunesSearchResponse = when {
                country == "US" -> ITunesSearchResponse(
                    resultCount = 3,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 10L,
                            artistId = 100L,
                            trackName = "You Were Beautiful",
                            artistName = "DAY6",
                            releaseDate = "2017-02-06T12:00:00Z",
                            trackTimeMillis = 283_160L,
                            previewUrl = officialPreview,
                        ),
                        ITunesSongDto(
                            trackId = 12L,
                            artistId = 100L,
                            trackName = "You Were Beautiful",
                            artistName = "DAY6",
                            releaseDate = "2017-02-06T12:00:00Z",
                            trackTimeMillis = 283_160L,
                            previewUrl = "https://audio.example/day6-album-copy.m4a",
                        ),
                        ITunesSongDto(
                            trackId = 11L,
                            artistId = 999L,
                            trackName = "You Were Beautiful",
                            artistName = "Shin Giwon",
                            releaseDate = "2020-02-02T12:00:00Z",
                            trackTimeMillis = 287_143L,
                            previewUrl = "https://audio.example/piano-cover.m4a",
                        ),
                    ),
                )
                term == "DAY6" -> ITunesSearchResponse(
                    resultCount = 1,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 3L,
                            artistId = 100L,
                            trackName = "예뻤어",
                            artistName = "데이식스",
                        ),
                    ),
                )
                else -> ITunesSearchResponse(
                    resultCount = 2,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 1L,
                            artistId = 100L,
                            trackName = "예뻤어",
                            artistName = "데이식스",
                            previewUrl = "https://audio.example/localized-official.m4a",
                        ),
                        ITunesSongDto(
                            trackId = 2L,
                            artistId = 999L,
                            trackName = "You Were Beautiful",
                            artistName = "신기원",
                            previewUrl = "https://audio.example/piano-cover.m4a",
                        ),
                    ),
                )
            }
        }
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse()
        }

        val result = MusicSearchRepository(api, artistApi)
            .findTrackMedia("You Were Beautiful", "DAY6")

        assertEquals(officialPreview, result?.previewUrl)
        assertEquals("DAY6", result?.artist)
    }

    @Test
    fun rejectsCrossStoreFallbackWhenExactMetadataPointsOnlyToAnotherArtist() = runBlocking {
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
            ) = if (country == "US") {
                ITunesSearchResponse(
                    resultCount = 1,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 20L,
                            artistId = 999L,
                            trackName = "You Were Beautiful",
                            artistName = "Shin Giwon",
                            previewUrl = "https://audio.example/piano-cover.m4a",
                        ),
                    ),
                )
            } else {
                ITunesSearchResponse()
            }
        }
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse()
        }

        val result = MusicSearchRepository(api, artistApi)
            .findTrackMedia("You Were Beautiful", "DAY6")

        assertNull(result)
    }

    @Test
    fun rejectsAmbiguousCrossStoreVersionsEvenWhenArtistAndTitleMatch() = runBlocking {
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
            ) = if (country == "US") {
                ITunesSearchResponse(
                    resultCount = 2,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 30L,
                            artistId = 100L,
                            trackName = "Same Name",
                            artistName = "Same Artist",
                            releaseDate = "2020-01-01T12:00:00Z",
                            trackTimeMillis = 180_000L,
                            previewUrl = "https://audio.example/first-version.m4a",
                        ),
                        ITunesSongDto(
                            trackId = 31L,
                            artistId = 100L,
                            trackName = "Same Name",
                            artistName = "Same Artist",
                            releaseDate = "2024-01-01T12:00:00Z",
                            trackTimeMillis = 220_000L,
                            previewUrl = "https://audio.example/second-version.m4a",
                        ),
                    ),
                )
            } else {
                ITunesSearchResponse()
            }
        }
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse()
        }

        val result = MusicSearchRepository(api, artistApi)
            .findTrackMedia("Same Name", "Same Artist")

        assertNull(result)
    }

    @Test
    fun resolvesLocalizedArtistByArtistIdWhenTrackTitleIsAmbiguous() = runBlocking {
        val correctPreview = "https://audio.example/illit.m4a"
        val correctArtwork = "https://image.example/illit/100x100bb.jpg"
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
            ) = if (term == "ILLIT") {
                ITunesSearchResponse(
                    resultCount = 1,
                    results = listOf(ITunesSongDto(trackId = 3L, artistId = 100L, trackName = "Magnetic", artistName = "아일릿")),
                )
            } else {
                ITunesSearchResponse(
                    resultCount = 2,
                    results = listOf(
                        ITunesSongDto(
                            trackId = 1L,
                            artistId = 100L,
                            trackName = "It's Me",
                            artistName = "아일릿",
                            artworkUrl100 = correctArtwork,
                            previewUrl = correctPreview,
                        ),
                        ITunesSongDto(
                            trackId = 2L,
                            artistId = 200L,
                            trackName = "It's Me",
                            artistName = "Chilli Beans.",
                            previewUrl = "https://audio.example/wrong.m4a",
                        ),
                    ),
                )
            }
        }
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse()
        }

        val resolved = MusicSearchRepository(api, artistApi).findTrackMedia("It's Me", "ILLIT")

        assertEquals(correctPreview, resolved?.previewUrl)
        assertTrue(resolved?.artworkUrl.orEmpty().contains("600x600bb"))
    }

    @Test
    fun previewSearchDoesNotWaitForDeezerArtistEnrichment() = runBlocking {
        var deezerCalls = 0
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
                        trackName = "Busy Boy",
                        artistName = "리센느",
                        previewUrl = "https://audio.example/busy-boy.m4a",
                    ),
                ),
            )
        }
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String): DeezerArtistSearchResponse {
                deezerCalls += 1
                return DeezerArtistSearchResponse()
            }
        }

        val result = MusicSearchRepository(api, artistApi).searchPreviews("Busy Boy RESCENE").single()

        assertEquals("https://audio.example/busy-boy.m4a", result.previewUrl)
        assertEquals(0, deezerCalls)
    }

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
    fun usesSupportedMelonGenreCatalog() = runBlocking {
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
        assertEquals(
            listOf(
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
            ),
            MusicSearchRepository(api, artistApi).genres(),
        )
    }

    @Test
    fun keepsRealDeezerArtistPhotoWhenDuplicateNameHasPlaceholder() = runBlocking {
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
                        trackId = 1L,
                        artistId = 2L,
                        trackName = "Magnetic",
                        artistName = "ILLIT",
                    ),
                ),
            )
        }
        val realImage = "https://cdn-images.dzcdn.net/images/artist/real/56x56.jpg"
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse(
                data = listOf(
                    DeezerArtistDto(name = "ILLIT ", picture_small = realImage),
                    DeezerArtistDto(
                        name = "ILLit",
                        picture_small = "https://cdn-images.dzcdn.net/images/artist/d41d8cd98f00b204e9800998ecf8427e/56x56.jpg",
                    ),
                ),
            )
        }

        val repository = MusicSearchRepository(api, artistApi)

        assertEquals(realImage, repository.search("ILLIT").single().artistImageUrl)
        assertEquals(realImage, repository.artistImage("ILLIT"))
    }

    @Test
    fun usesFirstRealDeezerPhotoWhenLocalizedArtistNameDiffers() = runBlocking {
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
                        trackId = 1L,
                        artistId = 2L,
                        trackName = "Love wins all",
                        artistName = "아이유",
                    ),
                ),
            )
        }
        val realImage = "https://cdn-images.dzcdn.net/images/artist/iu/56x56.jpg"
        val artistApi = object : DeezerArtistApi {
            override suspend fun search(artistName: String) = DeezerArtistSearchResponse(
                data = listOf(DeezerArtistDto(name = "IU", picture_small = realImage)),
            )
        }

        val result = MusicSearchRepository(api, artistApi).search("iu").single()

        assertEquals(realImage, result.artistImageUrl)
    }
}
