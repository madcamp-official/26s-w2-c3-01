package com.example.myapplication

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.ui.screens.InboxScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeConversationHidesEmptyStateNotice() {
        composeRule.setContent {
            MelodyBubbleTheme {
                InboxScreen(
                    chats = listOf(chat(hasMessages = true)),
                    onOpenChat = {},
                    onLeaveChat = {},
                )
            }
        }

        composeRule.onNodeWithText("민트").assertIsDisplayed()
        composeRule.onAllNodesWithText("맞팔이 성립된 사용자만 자유 메시지를 보낼 수 있어요")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("채팅 시작하기").assertCountEquals(0)
    }

    @Test
    fun emptyConversationOffersMutualSelection() {
        var openedRoomId: String? = null
        composeRule.setContent {
            MelodyBubbleTheme {
                InboxScreen(
                    chats = listOf(chat(hasMessages = false)),
                    onOpenChat = { openedRoomId = it },
                    onLeaveChat = {},
                )
            }
        }

        composeRule.onNodeWithText("채팅 시작하기").performClick()
        composeRule.onNodeWithText("대화할 맞팔 선택").assertIsDisplayed()
        composeRule.onNodeWithText("민트").performClick()

        composeRule.runOnIdle { assertEquals("room-mint", openedRoomId) }
    }

    @Test
    fun leavingConversationDoesNotLeaveSwipeBackgroundBehind() {
        composeRule.setContent {
            var chats by remember { mutableStateOf(listOf(chat(hasMessages = true))) }
            MelodyBubbleTheme {
                InboxScreen(
                    chats = chats,
                    onOpenChat = {},
                    onLeaveChat = { roomId ->
                        chats = chats.map { if (it.roomId == roomId) it.copy(isHidden = true) else it }
                    },
                )
            }
        }

        composeRule.onNodeWithText("민트").performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("채팅 시작하기").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("채팅방 나가기").assertCountEquals(0)
    }

    private fun chat(hasMessages: Boolean) = ChatPreview(
        roomId = "room-mint",
        peerHandle = "nearby-mint",
        peerAlias = "민트",
        peerColorHex = 0xFF25C76F,
        lastMessage = if (hasMessages) "이 곡 저도 좋아해요" else "",
        relativeTime = if (hasMessages) "방금" else "새 대화",
        unreadCount = 0,
        relationship = RelationshipStatus.MUTUAL,
        hasMessages = hasMessages,
    )
}
