package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.ui.screens.ChatScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sentAnimatedMessageSettlesFullyInsideLatestViewport() {
        val initialMessages = (1..18).map { index ->
            message(
                clientMessageId = "history-$index",
                content = "이전 메시지 $index",
                isMine = index % 2 == 0,
            )
        }

        composeRule.setContent {
            var messages by remember { mutableStateOf(initialMessages) }
            MelodyBubbleTheme {
                ChatScreen(
                    chat = ChatPreview(
                        roomId = "room",
                        peerHandle = "peer",
                        peerAlias = "민지",
                        peerColorHex = 0xFF8855FF,
                        lastMessage = initialMessages.last().content,
                        relativeTime = "방금",
                        unreadCount = 0,
                        relationship = RelationshipStatus.MUTUAL,
                    ),
                    messages = messages,
                    currentTrack = null,
                    onBack = {},
                    onSend = { content ->
                        messages = messages + message(
                            clientMessageId = "latest",
                            content = content,
                            isMine = true,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("가장 최신 메시지")
        composeRule.onNodeWithTag("chat_input").performImeAction()
        composeRule.onNodeWithTag("chat_message_latest").assertIsDisplayed()
    }

    private fun message(
        clientMessageId: String,
        content: String,
        isMine: Boolean,
    ) = ChatMessage(
        messageId = clientMessageId,
        clientMessageId = clientMessageId,
        roomId = "room",
        isMine = isMine,
        content = content,
        sentAtLabel = "방금",
        deliveryState = DeliveryState.SENT,
    )
}
