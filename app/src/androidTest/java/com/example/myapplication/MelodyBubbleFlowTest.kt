package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MelodyBubbleFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOutSessionShowsLoginEntry() {
        composeRule.onNodeWithText("MELODY BUBBLE").assertIsDisplayed()
        composeRule.onNodeWithText("이메일로 로그인").assertIsDisplayed()
    }
}
