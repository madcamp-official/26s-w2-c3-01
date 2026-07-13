package com.example.myapplication

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.core.model.DisplayPosition
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.Proximity
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.Track
import com.example.myapplication.ui.screens.UserDetailScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Rule
import org.junit.Test

class UserDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailUsesCompactProfileActionsWithoutCurrentTrackCard() {
        composeRule.setContent {
            MelodyBubbleTheme {
                UserDetailScreen(
                    listener = NearbyListener(
                        nearbyHandle = "nearby-mint",
                        profileHandle = "mint",
                        displayAlias = "민트",
                        colorHex = 0xFF25C76F,
                        displayPosition = DisplayPosition(0.5f, 0.5f),
                        matchScore = 82,
                        proximity = Proximity.WITHIN_10M,
                        isPlaying = true,
                        currentTrack = Track("track", "새벽의 온도", "Clouded Steps"),
                        commonGenres = listOf("Indie"),
                        relationship = RelationshipStatus.FOLLOWS_ME,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("리액션").assertIsDisplayed()
        composeRule.onNodeWithText("맞팔").assertIsDisplayed()
        composeRule.onAllNodesWithText("현재 듣는 음악").assertCountEquals(0)
        composeRule.onAllNodesWithText("음악 리액션 보내기").assertCountEquals(0)
        composeRule.onAllNodesWithText("맞팔하기").assertCountEquals(0)
    }
}
