package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MelodyBubbleFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingToNearbyCoreFlowIsReachable() {
        completeOnboardingWhenNeeded()

        composeRule.onNodeWithText("Melody Bubble").assertIsDisplayed()
        composeRule.onNodeWithText("근처").performClick()
        composeRule.onNodeWithText("취향 유사도").assertIsDisplayed()
        composeRule.onNodeWithText("실제 거리·방향·이동 경로와 무관해요.", substring = true)
            .assertIsDisplayed()
    }

    private fun completeOnboardingWhenNeeded() {
        repeat(2) {
            val continueButtons = composeRule.onAllNodesWithText("계속")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (continueButtons.isNotEmpty()) {
                composeRule.onNodeWithText("계속").performClick()
                composeRule.waitForIdle()
            }
        }
        val demoButtons = composeRule.onAllNodesWithText("데모 시작")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (demoButtons.isNotEmpty()) {
            composeRule.onNodeWithText("데모 시작").performClick()
            composeRule.waitForIdle()
        }
    }
}
