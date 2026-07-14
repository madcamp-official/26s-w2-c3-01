package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.ProfileSettings
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.ui.GenreCatalogUiState
import com.example.myapplication.ui.screens.MyScreen
import com.example.myapplication.ui.MusicSearchUiState
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Rule
import org.junit.Test

class MyProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun basicProfileDraftIsDiscardedWithoutSave() {
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = emptyProfile(),
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
                )
            }
        }

        composeRule.onNodeWithText("테스트 리스너").performClick()
        composeRule.onAllNodesWithText("DiceBear Lorelei Neutral 아바타").assertCountEquals(0)
        composeRule.onNodeWithText("장르").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("K-Pop").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_name_input").performTextReplacement("저장 안 한 이름")
        composeRule.onNodeWithText("닫기").performClick()

        composeRule.onNodeWithText("테스트 리스너").performClick()
        composeRule.onNodeWithTag("profile_name_input").assertTextContains("테스트 리스너")
        composeRule.onNodeWithText("무드").assertIsDisplayed()
    }

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
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
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
        composeRule.onNodeWithText("취향 정보가 쌓이면 여기에 보여요").assertIsDisplayed()
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
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
                )
            }
        }

        composeRule.onNodeWithTag("my_profile_list").performScrollToIndex(4)
        composeRule.onNodeWithText("아직 고른 곡이 없어요").performClick()

        composeRule.onNodeWithText("대표곡 편집").assertIsDisplayed()
        composeRule.onAllNodesWithText("프로필 이름").assertCountEquals(0)
        composeRule.onAllNodesWithText("최애 아티스트 편집").assertCountEquals(0)
    }

    @Test
    fun curatedSectionsUseTheSameRankFreeLayoutAsPublicProfiles() {
        val profile = emptyProfile().copy(
            signatureTracks = listOf(
                ProfileTrack(rank = 1, title = "새벽의 온도", artist = "Clouded Steps"),
            ),
            favoriteArtists = listOf(
                ProfileArtist(rank = 1, name = "루엘"),
            ),
        )
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = profile,
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
                )
            }
        }

        val list = composeRule.onNodeWithTag("my_profile_list")
        list.performScrollToIndex(4)
        composeRule.onNodeWithText("새벽의 온도").assertIsDisplayed()
        composeRule.onAllNodesWithText("1").assertCountEquals(0)
        list.performScrollToIndex(5)
        composeRule.onNodeWithText("루엘").assertIsDisplayed()
    }

    @Test
    fun trackEditorScrollsThirtyResultsWhileSearchAndSaveStayVisible() {
        val results = (1L..30L).map { index ->
            MusicSearchResult(
                id = index,
                artistId = index,
                title = "검색 곡 $index",
                artist = "아티스트 $index",
                album = "앨범 $index",
                genre = "K-Pop",
                releaseDate = null,
                durationSeconds = 180,
                artworkUrl = null,
                previewUrl = null,
                appleMusicUrl = null,
            )
        }
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = emptyProfile(),
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Success("검색", results),
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
                )
            }
        }

        composeRule.onNodeWithTag("my_profile_list").performScrollToIndex(4)
        composeRule.onNodeWithText("아직 고른 곡이 없어요").performClick()
        composeRule.onNodeWithTag("profile_track_results").performScrollToIndex(29)

        composeRule.onNodeWithText("검색 곡 30").assertIsDisplayed()
        composeRule.onNodeWithText("곡 또는 아티스트 검색").assertIsDisplayed()
        composeRule.onNodeWithText("대표곡 저장").assertIsDisplayed()
    }

    @Test
    fun selectedItemsOpenInASeparateDialogWithoutShrinkingSearchResults() {
        val profile = emptyProfile().copy(
            signatureTracks = listOf(
                ProfileTrack(rank = 1, title = "새벽의 온도", artist = "Clouded Steps"),
            ),
        )
        composeRule.setContent {
            MelodyBubbleTheme {
                MyScreen(
                    profile = profile,
                    profileSaving = false,
                    feedbackMessage = null,
                    followingCount = 0,
                    followerCount = 0,
                    nowPlayingTrack = null,
                    nowPlayingActive = false,
                    onLoadConnections = {},
                    onOpenFollowing = {},
                    onOpenFollowers = {},
                    onOpenSettings = {},
                    onProfileUpdate = { _, _, _, _, _ -> },
                    onProfileCurationUpdate = { _, _ -> },
                    musicSearchState = MusicSearchUiState.Idle,
                    genreCatalogState = GenreCatalogUiState(genres = listOf("K-Pop", "록")),
                    onRetryGenreCatalog = {},
                    onSearchMusic = {},
                    onClearMusicSearch = {},
                    onPreviewMusic = {},
                    onCustomizeAvatar = {},
                )
            }
        }

        composeRule.onNodeWithTag("my_profile_list").performScrollToIndex(4)
        composeRule.onNodeWithText("새벽의 온도").performClick()
        composeRule.onNodeWithTag("music_selected_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("selected_curation_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("선택한 대표곡").assertIsDisplayed()
        composeRule.onNodeWithText("1/3 선택됨").assertIsDisplayed()
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
    )
}
