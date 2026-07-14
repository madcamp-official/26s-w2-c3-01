package com.example.myapplication.ui

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LocationLoungeErrorMessageTest {
    @Test
    fun networkFailureIsNotReportedAsLoungeConflict() {
        assertEquals(
            "네트워크에 연결할 수 없어요. 연결 상태를 확인하고 다시 시도해 주세요.",
            locationLoungeErrorMessage(IOException("offline"), "fallback"),
        )
    }

    @Test
    fun onlyConflictStatusIsReportedAsLoungeConflict() {
        assertEquals(
            "다른 라운지 반경 안에서는 새 라운지를 만들 수 없어요.",
            locationLoungeErrorMessage(httpError(409), "fallback"),
        )
    }

    @Test
    fun missingDeploymentIsReportedSeparately() {
        assertEquals(
            "배포 서버가 새 라운지 기능을 아직 지원하지 않아요.",
            locationLoungeErrorMessage(httpError(404), "fallback"),
        )
    }

    @Test
    fun authenticationAndServerFailuresHaveSpecificMessages() {
        assertEquals(
            "로그인 세션이 만료됐어요. 다시 로그인해 주세요.",
            locationLoungeErrorMessage(httpError(401), "fallback"),
        )
        assertEquals(
            "서버 오류로 라운지를 처리하지 못했어요. 잠시 후 다시 시도해 주세요.",
            locationLoungeErrorMessage(httpError(503), "fallback"),
        )
    }

    private fun httpError(code: Int): HttpException = HttpException(
        Response.error<Unit>(
            code,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
    )
}
