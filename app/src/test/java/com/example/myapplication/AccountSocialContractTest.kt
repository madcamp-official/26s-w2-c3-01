package com.example.myapplication

import com.example.myapplication.data.remote.AuthApi
import com.example.myapplication.data.remote.SignupRequest
import com.example.myapplication.data.remote.SocialApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSocialContractTest {
    @Test
    fun `signup sends password confirmation and checks email availability`() {
        val request = SignupRequest("listener@example.com", "password1", "password1", "멜로디")

        assertEquals(request.password, request.passwordConfirmation)
        assertTrue(AuthApi::class.java.methods.any { it.name == "emailAvailability" })
    }

    @Test
    fun `social client exposes both connection lists and stable unfollow`() {
        val methods = SocialApi::class.java.methods.map { it.name }

        assertTrue("following" in methods)
        assertTrue("followers" in methods)
        assertTrue("unfollowRelationship" in methods)
    }
}
