package com.example.myapplication.offlineexchange

import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.MelodyDatabase
import com.example.myapplication.data.local.OfflineAccountStore
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.jwtSubject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class OfflineExchangeSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val account = OfflineAccountStore(applicationContext).load() ?: return Result.success()
        val session = SecureTokenStore(applicationContext).load() ?: return Result.success()
        if (jwtSubject(session.accessToken) != account.accountId) return Result.failure()
        val database = MelodyDatabase.getInstance(applicationContext)
        val engine = OfflineExchangeSyncEngine(
            database.offlineExchangeDao(),
            database.syncOutboxDao(),
            ApiClient.createOfflineExchangeApi(),
        )
        return try {
            engine.sync(account.accountId, session.accessToken)
            applicationContext.sendBroadcast(
                Intent(ACTION_SYNC_COMPLETED).setPackage(applicationContext.packageName)
            )
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val storedAfterFailure = SecureTokenStore(applicationContext).load()
            if (error is retrofit2.HttpException && error.code() == 401 &&
                storedAfterFailure?.refreshToken == null
            ) {
                ApiClient.invalidateSession()
            }
            when (decideSyncFailure(
                error,
                hasRefreshableSession = storedAfterFailure?.refreshToken != null,
            )) {
                SyncFailureDecision.RETRY -> Result.retry()
                SyncFailureDecision.FAIL -> Result.failure()
            }
        }
    }

    companion object {
        const val ACTION_SYNC_COMPLETED =
            "com.example.myapplication.action.OFFLINE_EXCHANGE_SYNC_COMPLETED"
    }

}

object OfflineExchangeSyncScheduler {
    private const val UNIQUE_WORK = "offline-exchange-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<OfflineExchangeSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }
}
