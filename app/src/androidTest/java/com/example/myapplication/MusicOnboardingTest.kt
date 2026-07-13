package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.ui.MusicSearchUiState
import com.example.myapplication.ui.screens.OnboardingScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicOnboardingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedGenreArtistAndTrackAreReturnedAtCompletion() {
        val result = MusicSearchResult(
            id = 123L,
            artistId = 456L,
            title = "밤편지",
            artist = "아이유",
            album = "Palette",
            genre = "K-Pop",
            releaseDate = null,
            durationSeconds = 253,
            artworkUrl = null,
            previewUrl = null,
            appleMusicUrl = null,
        )
        var completedArtists = emptyList<String>()
        var completedTracks = emptyList<String>()

        composeRule.setContent {
            MelodyBubbleTheme {
                OnboardingScreen(
                    musicSearchState = MusicSearchUiState.Success("아이유", listOf(result)),
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onComplete = { _, _, artists, tracks ->
                        completedArtists = artists.map { it.name }
                        completedTracks = tracks.map { it.title }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_terms_checkbox").performClick()
        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNodeWithText("좋아하는 장르를 골라요").assertIsDisplayed()
        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNodeWithText("아이유").performClick()
        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNodeWithText("밤편지").performClick()
        composeRule.onNodeWithText("계속").performClick()
        composeRule.onNodeWithText("시작하기").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("아이유"), completedArtists)
            assertEquals(listOf("밤편지"), completedTracks)
        }
    }
}
