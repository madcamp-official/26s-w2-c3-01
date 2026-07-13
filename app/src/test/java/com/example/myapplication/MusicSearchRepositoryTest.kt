package com.example.myapplication

import com.example.myapplication.data.remote.ITunesSearchResponse
import com.example.myapplication.data.remote.ITunesSongDto
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

        val song = MusicSearchRepository(api).search(" 아이유 ").single()

        assertEquals(123L, song.id)
        assertEquals("밤편지", song.title)
        assertEquals("아이유", song.artist)
        assertEquals(253, song.durationSeconds)
        assertTrue(song.artworkUrl.orEmpty().contains("600x600bb"))
    }
}
