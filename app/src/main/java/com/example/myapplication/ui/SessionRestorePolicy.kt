package com.example.myapplication.ui

import java.io.IOException
import retrofit2.HttpException

internal enum class SessionRestoreDecision {
    OFFER_OFFLINE,
    REQUIRE_REAUTHENTICATION,
    SIGN_OUT,
}
internal fun decideSessionRestore(error: Throwable, hasCachedAccount: Boolean): SessionRestoreDecision = when {
    error is HttpException && error.code() in setOf(401, 403) ->
        SessionRestoreDecision.REQUIRE_REAUTHENTICATION
    (error is IOException || error.cause is IOException) && hasCachedAccount ->
        SessionRestoreDecision.OFFER_OFFLINE
    hasCachedAccount -> SessionRestoreDecision.OFFER_OFFLINE
    else -> SessionRestoreDecision.SIGN_OUT
}
