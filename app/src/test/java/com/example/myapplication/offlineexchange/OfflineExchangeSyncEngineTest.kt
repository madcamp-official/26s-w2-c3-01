package com.example.myapplication.offlineexchange

import com.example.myapplication.data.local.OfflineExchangeDao
import com.example.myapplication.data.local.OfflineExchangeEntity
import com.example.myapplication.data.local.SyncOutboxDao
import com.example.myapplication.data.local.SyncOutboxEntity
import com.example.myapplication.data.remote.OfflineCredentialRequest
import com.example.myapplication.data.remote.OfflineExchangeApi
import com.example.myapplication.data.remote.OfflineExchangeBatchRequest
import com.example.myapplication.data.remote.OfflineExchangeSyncResult
import com.example.myapplication.data.remote.RemoteOfflineExchange
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineExchangeSyncEngineTest {
    @Test
    fun `pending and interrupted uploading records are sent and marked synced`() = runBlocking {
        val store = FakeExchangeDao(mutableListOf(record("pending", "PENDING"), record("interrupted", "UPLOADING")))
        val outbox = FakeOutboxDao(store.records.mapTo(mutableListOf()) { action(it.id) })
        val remote = FakeOfflineExchangeApi()

        OfflineExchangeSyncEngine(store, outbox, remote).sync(OWNER, TOKEN)

        assertEquals(listOf(2), remote.uploadSizes)
        assertTrue(store.records.all { it.syncState == "SYNCED" })
        assertTrue(outbox.actions.isEmpty())
    }

    @Test
    fun `large outbox is uploaded in server sized batches`() = runBlocking {
        val records = (1..51).mapTo(mutableListOf()) { record("exchange-$it", "PENDING") }
        val store = FakeExchangeDao(records)
        val outbox = FakeOutboxDao(records.mapTo(mutableListOf()) { action(it.id) })
        val remote = FakeOfflineExchangeApi()

        OfflineExchangeSyncEngine(store, outbox, remote).sync(OWNER, TOKEN)

        assertEquals(listOf(50, 1), remote.uploadSizes)
        assertTrue(records.all { it.syncState == "SYNCED" })
    }

    @Test
    fun `network failure records retry metadata and remains retryable`() = runBlocking {
        val records = mutableListOf(record("failed", "PENDING"))
        val store = FakeExchangeDao(records)
        val outbox = FakeOutboxDao(mutableListOf(action("failed")))
        val remote = FakeOfflineExchangeApi(uploadFailure = IOException("offline"))

        val error = runCatching {
            OfflineExchangeSyncEngine(store, outbox, remote).sync(OWNER, TOKEN)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("FAILED", records.single().syncState)
        assertEquals(1, records.single().retryCount)
        assertEquals("NETWORK", records.single().lastError)
    }

    @Test
    fun `syncing one account never changes another accounts records`() = runBlocking {
        val accountA = record("exchange-a", "PENDING")
        val accountB = record("exchange-b", "PENDING").copy(ownerUserId = "account-b")
        val records = mutableListOf(accountA, accountB)
        val store = FakeExchangeDao(records)
        val outbox = FakeOutboxDao(mutableListOf(action("exchange-a"), action("exchange-b").copy(ownerUserId = "account-b")))

        OfflineExchangeSyncEngine(store, outbox, FakeOfflineExchangeApi()).sync(OWNER, TOKEN)

        assertEquals("SYNCED", records.first { it.ownerUserId == OWNER }.syncState)
        assertEquals("PENDING", records.first { it.ownerUserId == "account-b" }.syncState)
    }

    private fun record(id: String, state: String) = OfflineExchangeEntity(
        id = id,
        ownerUserId = OWNER,
        localSessionId = "session-$id",
        credentialId = "credential-local",
        peerCredentialId = "credential-peer",
        peerDisplayAlias = "Peer",
        trackTitle = "Song",
        trackArtist = "Artist",
        melodyAlias = "C6",
        sentCardJson = "{}",
        receivedCardJson = "{}",
        exchangedAt = 1L,
        syncState = state,
        expiresAt = null,
        payloadHash = "hash",
        recordSignature = "signature",
    )

    private fun action(exchangeId: String) = SyncOutboxEntity(
        id = "outbox-$exchangeId",
        ownerUserId = OWNER,
        kind = "OFFLINE_EXCHANGE",
        requestId = exchangeId,
        payloadJson = "{}",
        createdAt = 1L,
    )

    private class FakeExchangeDao(val records: MutableList<OfflineExchangeEntity>) : OfflineExchangeDao {
        override fun observeForOwner(ownerUserId: String): Flow<List<OfflineExchangeEntity>> =
            flowOf(records.filter { it.ownerUserId == ownerUserId })

        override suspend fun pendingForOwner(ownerUserId: String): List<OfflineExchangeEntity> =
            records.filter { it.ownerUserId == ownerUserId && it.syncState in setOf("PENDING", "UPLOADING", "FAILED") }

        override suspend fun find(ownerUserId: String, exchangeId: String) =
            records.firstOrNull { it.ownerUserId == ownerUserId && it.id == exchangeId }

        override suspend fun insert(exchange: OfflineExchangeEntity) {
            records.removeAll { it.ownerUserId == exchange.ownerUserId && it.id == exchange.id }
            records += exchange
        }

        override suspend fun insertAll(exchanges: List<OfflineExchangeEntity>) = exchanges.forEach { insert(it) }
        override suspend fun update(exchange: OfflineExchangeEntity) = insert(exchange)

        override suspend fun updateSyncState(
            ownerUserId: String,
            exchangeId: String,
            state: String,
            retryCount: Int,
            lastError: String?,
        ) {
            val index = records.indexOfFirst { it.ownerUserId == ownerUserId && it.id == exchangeId }
            if (index >= 0) records[index] = records[index].copy(
                syncState = state,
                retryCount = retryCount,
                lastError = lastError,
            )
        }

        override suspend fun delete(ownerUserId: String, exchangeId: String) {
            records.removeAll { it.ownerUserId == ownerUserId && it.id == exchangeId }
        }

        override suspend fun deleteExpired(ownerUserId: String, now: Long) {
            records.removeAll { it.ownerUserId == ownerUserId && it.expiresAt?.let { expiry -> expiry < now } == true }
        }
    }

    private class FakeOutboxDao(val actions: MutableList<SyncOutboxEntity>) : SyncOutboxDao {
        override fun observeForOwner(ownerUserId: String): Flow<List<SyncOutboxEntity>> =
            flowOf(actions.filter { it.ownerUserId == ownerUserId })

        override suspend fun pendingForOwner(ownerUserId: String) = actions.filter { it.ownerUserId == ownerUserId }
        override suspend fun insert(action: SyncOutboxEntity) {
            actions.removeAll { it.ownerUserId == action.ownerUserId && it.id == action.id }
            actions += action
        }
        override suspend fun delete(ownerUserId: String, id: String) {
            actions.removeAll { it.ownerUserId == ownerUserId && it.id == id }
        }
    }

    private class FakeOfflineExchangeApi(
        private val uploadFailure: Throwable? = null,
    ) : OfflineExchangeApi {
        val uploadSizes = mutableListOf<Int>()

        override suspend fun issueCredential(
            authorization: String,
            request: OfflineCredentialRequest,
        ): OfflineCredential = error("not used")

        override suspend fun upload(
            authorization: String,
            request: OfflineExchangeBatchRequest,
        ): List<OfflineExchangeSyncResult> {
            uploadFailure?.let { throw it }
            uploadSizes += request.items.size
            return request.items.map { OfflineExchangeSyncResult(it.exchangeId, "UNCONFIRMED") }
        }

        override suspend fun history(authorization: String): List<RemoteOfflineExchange> = emptyList()
        override suspend fun delete(authorization: String, exchangeId: String) = Unit
    }

    private companion object {
        const val OWNER = "account-a"
        const val TOKEN = "access-token"
    }
}
