package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import com.example.myapplication.core.model.ProfileSettings
import com.example.myapplication.ui.screens.MyScreen
import com.example.myapplication.ui.MusicSearchUiState
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Rule
import org.junit.Test

class MyProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyProfileKeepsMusicFeaturesDiscoverable() {
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = emptyProfile(),
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    verifiedOfflineExchangeCount = 0,
                    offlineExchangeGenres = emptyList(),
                    offlineExchangeMoods = emptyList(),
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onOpenBubbleMode = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onRandomizeAvatar = {},
                    onOpenMelodyAlias = {},
                )
            }
        }

        val list = composeRule.onNodeWithTag("my_profile_list")
        list.performScrollToIndex(2)
        composeRule.onNodeWithText("재생 중인 음악이 아직 없어요").assertIsDisplayed()
        list.performScrollToIndex(4)
        composeRule.onNodeWithText("아직 고른 곡이 없어요").assertIsDisplayed()
        list.performScrollToIndex(5)
        composeRule.onNodeWithText("좋아하는 아티스트를 알려주세요").assertIsDisplayed()
        list.performScrollToIndex(6)
        composeRule.onNodeWithText("교환 데이터가 쌓이면 취향이 보여요").assertIsDisplayed()
    }

    @Test
    fun tappingSignatureTracksOpensOnlyTrackEditor() {
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = emptyProfile(),
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    verifiedOfflineExchangeCount = 0,
                    offlineExchangeGenres = emptyList(),
                    offlineExchangeMoods = emptyList(),
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onOpenBubbleMode = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onRandomizeAvatar = {},
                    onOpenMelodyAlias = {},
                )
            }
        }

        composeRule.onNodeWithTag("my_profile_list").performScrollToIndex(4)
        composeRule.onNodeWithText("아직 고른 곡이 없어요").performClick()

        composeRule.onNodeWithText("대표곡 편집").assertIsDisplayed()
        composeRule.onAllNodesWithText("프로필 이름").assertCountEquals(0)
        composeRule.onAllNodesWithText("최애 아티스트 편집").assertCountEquals(0)
    }

    private fun emptyProfile() = ProfileSettings(
        accountAlias = "테스트 리스너",
        nearbyDisplayAlias = "테스트 리스너",
        colorHex = 0xFF43DD7D,
        bio = "",
        avatarSeed = "test-listener",
        avatarUrl = null,
        genres = emptyList(),
        moods = emptyList(),
        melodyNotes = emptyList(),
        melodyAliasId = "",
        melodyAliasTone = "",
        melodyAliasMood = "",
        melodyAliasTempo = 0,
        musicVisibilityLabel = "제목과 아티스트 공개",
        discoverable = true,
        allowReactions = true,
        offlineExchangeEnabled = true,
    )
}
