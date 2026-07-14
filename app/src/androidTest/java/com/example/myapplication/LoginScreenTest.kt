package com.example.myapplication

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.myapplication.ui.EmailAvailabilityUiState
import com.example.myapplication.ui.LoginUiState
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.theme.MelodyBubbleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun existingAccountCanSubmitLegacyPassword() {
        var submittedEmail: String? = null
        var submittedPassword: String? = null
        composeRule.setContent {
            MelodyBubbleTheme {
                LoginScreen(
                    state = LoginUiState.Idle,
                    emailAvailabilityState = EmailAvailabilityUiState.Idle,
                    onLogin = { email, password ->
                        submittedEmail = email
                        submittedPassword = password
                    },
                    onSignup = { _, _, _, _ -> },
                    onCheckEmail = {},
                    onEmailChanged = {},
                    onGoogleLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("이메일로 로그인").performClick()
        composeRule.onNodeWithTag("login_email").performTextInput("listener@example.com")
        composeRule.onNodeWithTag("login_password").performTextInput("1234")
        composeRule.onNodeWithTag("email_login_submit").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("listener@example.com", submittedEmail)
            assertEquals("1234", submittedPassword)
        }
    }

    @Test
    fun incompleteEmailExplainsWhyLoginIsDisabled() {
        composeRule.setContent {
            MelodyBubbleTheme {
                LoginScreen(
                    state = LoginUiState.Idle,
                    emailAvailabilityState = EmailAvailabilityUiState.Idle,
                    onLogin = { _, _ -> },
                    onSignup = { _, _, _, _ -> },
                    onCheckEmail = {},
                    onEmailChanged = {},
                    onGoogleLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("이메일로 로그인").performClick()
        composeRule.onNodeWithTag("login_email").performTextInput("listener")
        composeRule.onNodeWithTag("login_password").performTextInput("1234")

        composeRule.onNodeWithText("이메일 주소 전체를 입력해 주세요. 예: name@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag("email_login_submit").assertIsNotEnabled()
    }
}
