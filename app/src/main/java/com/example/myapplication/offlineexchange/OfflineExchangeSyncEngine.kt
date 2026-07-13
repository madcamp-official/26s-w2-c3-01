package com.example.myapplication.offlineexchange

import com.example.myapplication.core.model.SyncState
import com.example.myapplication.data.local.OfflineExchangeDao
import com.example.myapplication.data.local.OfflineExchangeEntity
import com.example.myapplication.data.local.SyncOutboxDao
import com.example.myapplication.data.remote.OfflineExchangeApi
import com.example.myapplication.data.remote.OfflineExchangeBatchRequest
import com.example.myapplication.data.remote.OfflineExchangeUpload
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

internal class OfflineExchangeSyncEngine(
    private val dao: OfflineExchangeDao,
    private val outbox: SyncOutboxDao,
    private val api: OfflineExchangeApi,
) {
    suspend fun sync(ownerUserId: String, accessToken: String) {
        val actions = outbox.pendingForOwner(ownerUserId)
        val pending = dao.pendingForOwner(ownerUserId)
            .filter { it.credentialId != null && it.peerCredentialId != null && it.recordSignature.isNotBlank() }
        val completedIds = mutableSetOf<String>()
        try {
            actions.filter { it.kind == "OFFLINE_EXCHANGE_DELETE" }.forEach { action ->
                runCatching { api.delete("Bearer $accessToken", action.requestId) }
                    .onFailure { error ->
                        if (error !is HttpException || error.code() != 404) throw error
                    }
                outbox.delete(ownerUserId, action.id)
            }
            pending.chunked(50).forEach { batch ->
                batch.forEach {
                    dao.updateSyncState(ownerUserId, it.id, SyncState.UPLOADING.name, it.retryCount, null)
                }
                api.upload(
                    "Bearer $accessToken",
                    OfflineExchangeBatchRequest(batch.map(::toUpload)),
                )
                batch.forEach {
                    dao.updateSyncState(ownerUserId, it.id, SyncState.SYNCED.name, it.retryCount, null)
                    outbox.delete(ownerUserId, "outbox-${it.id}")
                    completedIds += it.id
                }
            }

            api.history("Bearer $accessToken").forEach { remote ->
                if (dao.find(ownerUserId, remote.exchangeId) == null) {
                    val card = runCatching { ExchangeProtocol.cardFromJson(remote.receivedCardJson) }.getOrNull()
                    dao.insert(
                        OfflineExchangeEntity(
                            id = remote.exchangeId,
                            ownerUserId = ownerUserId,
                            localSessionId = UUID.randomUUID().toString(),
                            credentialId = null,
                            peerCredentialId = null,
                            peerDisplayAlias = remote.peerDisplayAlias,
                            trackTitle = card?.trackTitle.orEmpty(),
                            trackArtist = card?.trackArtist.orEmpty(),
                            melodyAlias = card?.melodyAlias.orEmpty(),
                            sentCardJson = remote.sentCardJson,
                            receivedCardJson = remote.receivedCardJson,
                            exchangedAt = remote.deviceOccurredAt,
                            syncState = SyncState.SYNCED.name,
                            expiresAt = null,
                            payloadHash = remote.payloadHash,
                            protocolVersion = remote.protocolVersion,
                            recordSignature = "",
                        )
                    )
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            pending.filterNot { it.id in completedIds }.forEach {
                dao.updateSyncState(
                    ownerUserId,
                    it.id,
                    SyncState.FAILED.name,
                    it.retryCount + 1,
                    syncError(error),
                )
            }
            throw error
        }
    }

    private fun toUpload(entity: OfflineExchangeEntity) = OfflineExchangeUpload(
        exchangeId = entity.id,
        credentialId = requireNotNull(entity.credentialId),
        peerCredentialId = requireNotNull(entity.peerCredentialId),
        sentCardJson = entity.sentCardJson,
        receivedCardJson = entity.receivedCardJson,
        deviceOccurredAt = entity.exchangedAt,
        payloadHash = entity.payloadHash,
        protocolVersion = entity.protocolVersion,
        recordSignature = entity.recordSignature,
    )

    private fun syncError(error: Throwable): String = when (error) {
        is HttpException -> "HTTP ${error.code()}"
        is IOException -> "NETWORK"
        else -> error.message?.take(160) ?: "UNKNOWN"
    }
}
