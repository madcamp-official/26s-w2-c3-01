package com.example.myapplication.ui

import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SessionRestorePolicyTest {
    @Test
    fun `network failure with cached account offers offline mode without signing out`() {
        assertEquals(
            SessionRestoreDecision.OFFER_OFFLINE,
            decideSessionRestore(IOException("offline"), hasCachedAccount = true),
        )
    }

    @Test
    fun `network failure without cached account signs out`() {
        assertEquals(
            SessionRestoreDecision.SIGN_OUT,
            decideSessionRestore(IOException("offline"), hasCachedAccount = false),
        )
    }

    @Test
    fun `rejected token requires authentication even when a cache exists`() {
        val error = HttpException(Response.error<Unit>(401, "unauthorized".toResponseBody()))

        assertEquals(
            SessionRestoreDecision.REQUIRE_REAUTHENTICATION,
            decideSessionRestore(error, hasCachedAccount = true),
        )
    }
}
