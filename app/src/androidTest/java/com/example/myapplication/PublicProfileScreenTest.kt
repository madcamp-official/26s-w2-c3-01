package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.myapplication.core.model.CommonTasteMetric
import com.example.myapplication.core.model.CommonTasteSummary
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.ProfileNowPlaying
import com.example.myapplication.core.model.ProfileStats
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.core.model.PublicProfile
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.SharedFollowerPreview
import com.example.myapplication.core.model.TasteFingerprint
import com.example.myapplication.core.model.TasteMetric
import com.example.myapplication.ui.screens.PublicProfileScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PublicProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tasteAndRelationshipLeadToOptionalFollow() {
        var followed = false
        composeRule.setContent {
            MelodyBubbleTheme {
                PublicProfileScreen(
                    profile = PublicProfile(
                        profileHandle = "listener_night",
                        displayName = "Night Listener",
                        colorHex = 0xFF25C76F,
                        bio = "밤에 어울리는 음악을 모아요.",
                        avatarSeed = "night-listener",
                        avatarUrl = null,
                        genres = listOf("R&B", "Indie"),
                        moods = listOf("Night"),
                        melodyAlias = null,
                        stats = ProfileStats(followerCount = 3),
                        tasteFingerprint = TasteFingerprint(
                            genres = listOf(TasteMetric("R&B", 3, 0.75)),
                        ),
                        relationship = RelationshipStatus.NONE,
                        following = false,
                        mutual = false,
                    ),
                    loading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onFollow = { followed = true },
                )
            }
        }

        composeRule.onNodeWithText("Night Listener").assertIsDisplayed()
        composeRule.onAllNodesWithText("R&B").assertCountEquals(2)
        composeRule.onNodeWithText("팔로우").performClick()

        composeRule.runOnIdle { assertTrue(followed) }
    }

    @Test
    fun curatedLiveProfileRendersCompactSections() {
        var messageRequested = false
        composeRule.setContent {
            MelodyBubbleTheme {
                PublicProfileScreen(
                    profile = PublicProfile(
                        profileHandle = "mintwave",
                        displayName = "민트",
                        colorHex = 0xFF8B5CF6,
                        bio = "잔잔한 멜로디를 나눠요.",
                        avatarSeed = "mintwave",
                        avatarUrl = null,
                        genres = listOf("Indie"),
                        moods = listOf("Calm", "Night"),
                        melodyAlias = null,
                        stats = ProfileStats(followingCount = 12, followerCount = 34),
                        tasteFingerprint = TasteFingerprint(),
                        relationship = RelationshipStatus.MUTUAL,
                        following = true,
                        mutual = true,
                        sharedFollowers = listOf(
                            SharedFollowerPreview("listener_one", "해나", null),
                            SharedFollowerPreview("listener_two", "민수", null),
                        ),
                        sharedFollowerCount = 4,
                        signatureTracks = listOf(
                            ProfileTrack(rank = 1, title = "새벽의 온도", artist = "Clouded Steps"),
                        ),
                        favoriteArtists = listOf(ProfileArtist(rank = 1, name = "루엘")),
                        nowPlaying = ProfileNowPlaying(
                            title = "햇살이 번지는 계절",
                            artist = "나른한 오후의 피아노",
                            isPlaying = true,
                            durationMs = 240_000,
                            positionMs = 120_000,
                            observedAt = "2026-07-13T00:00:00Z",
                            expiresAt = "2026-07-13T00:01:30Z",
                        ),
                        commonTaste = CommonTasteSummary(
                            score = 87,
                            metrics = listOf(CommonTasteMetric("잔잔한 멜로디", "MOOD", 89, 3)),
                            algorithmVersion = "COMMON_TASTE_V1",
                            sampleSize = 8,
                            calculatedAt = "2026-07-13T00:00:00Z",
                        ),
                    ),
                    loading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onFollow = {},
                    onMessage = { messageRequested = true },
                )
            }
        }

        composeRule.onNodeWithText("해나, 민수 외 2명이 팔로우해요").assertIsDisplayed()
        composeRule.onNodeWithText("메시지 보내기").performClick()
        composeRule.runOnIdle { assertTrue(messageRequested) }
        composeRule.onNodeWithText("지금 듣는 음악").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("요즘 나를 설명하는 3곡").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("1").assertCountEquals(0)
        composeRule.onNodeWithTag("public_profile_list").performScrollToIndex(4)
        composeRule.onNodeWithText("최애 아티스트 3명").assertIsDisplayed()
        composeRule.onNodeWithTag("public_profile_list").performScrollToIndex(5)
        composeRule.onNodeWithText("87%").assertIsDisplayed()
    }
}
