package com.example.myapplication.offlineexchange

import java.io.IOException
import retrofit2.HttpException

internal enum class SyncFailureDecision { RETRY, FAIL }

internal fun decideSyncFailure(error: Throwable, hasRefreshableSession: Boolean): SyncFailureDecision = when {
    error is IOException -> SyncFailureDecision.RETRY
    error is HttpException && error.code() >= 500 -> SyncFailureDecision.RETRY
    error is HttpException && error.code() == 429 -> SyncFailureDecision.RETRY
    error is HttpException && error.code() == 401 && hasRefreshableSession -> SyncFailureDecision.RETRY
    else -> SyncFailureDecision.FAIL
}
