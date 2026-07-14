package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.SecureTokenStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MelodyBubbleFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startFromSignedOutSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SecureTokenStore(context).clear()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun signedOutSessionShowsLoginEntry() {
        composeRule.onNodeWithContentDescription("SYNC").assertIsDisplayed()
        composeRule.onNodeWithText("이메일로 로그인").assertIsDisplayed()
    }
}
