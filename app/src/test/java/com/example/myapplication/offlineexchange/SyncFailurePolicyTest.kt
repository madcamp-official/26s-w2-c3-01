package com.example.myapplication.offlineexchange

import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncFailurePolicyTest {
    @Test
    fun `network and temporary server failures retry`() {
        assertEquals(SyncFailureDecision.RETRY, decideSyncFailure(IOException("offline"), true))
        assertEquals(SyncFailureDecision.RETRY, decideSyncFailure(http(503), true))
        assertEquals(SyncFailureDecision.RETRY, decideSyncFailure(http(429), true))
    }

    @Test
    fun `401 retries only while refreshable session remains`() {
        assertEquals(SyncFailureDecision.RETRY, decideSyncFailure(http(401), true))
        assertEquals(SyncFailureDecision.FAIL, decideSyncFailure(http(401), false))
    }

    @Test
    fun `invalid signature response is permanent`() {
        assertEquals(SyncFailureDecision.FAIL, decideSyncFailure(http(400), true))
    }

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "error".toResponseBody()))
}
