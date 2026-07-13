package com.example.myapplication

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import com.example.myapplication.data.local.MelodyDatabase
import com.example.myapplication.data.local.OfflineExchangeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineExchangeDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MelodyDatabase::class.java,
    )
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        MelodyDatabase::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = database.close()

    @Test
    fun exchangeHistoryIsIsolatedByOwnerAccount() = runBlocking {
        database.offlineExchangeDao().insert(exchange("exchange-a", "account-a"))
        database.offlineExchangeDao().insert(exchange("exchange-b", "account-b"))

        assertEquals(listOf("exchange-a"), database.offlineExchangeDao().observeForOwner("account-a").first().map { it.id })
        assertEquals(listOf("exchange-b"), database.offlineExchangeDao().observeForOwner("account-b").first().map { it.id })
    }

    @Test
    fun versionTwoDemoRowsAreQuarantinedDuringMigration() {
        val name = "offline-exchange-migration-test"
        migrationHelper.createDatabase(name, 2).apply {
            execSQL(
                """INSERT INTO offline_exchange_local
                    (id,localSessionId,peerDisplayAlias,trackTitle,trackArtist,melodyAlias,exchangedAt,syncState,expiresAt)
                    VALUES ('legacy','session','Peer','Song','Artist','C6',1,'PENDING',NULL)"""
            )
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(
            name,
            3,
            true,
            MelodyDatabase.MIGRATION_2_3,
        )
        migrated.query("SELECT ownerUserId FROM offline_exchange_local WHERE id='legacy'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("__legacy__", cursor.getString(0))
        }
        migrated.close()
    }

    private fun exchange(id: String, owner: String) = OfflineExchangeEntity(
        id = id,
        ownerUserId = owner,
        localSessionId = "session-$id",
        credentialId = "credential-$owner",
        peerCredentialId = "peer-$owner",
        peerDisplayAlias = "Peer",
        trackTitle = "Song",
        trackArtist = "Artist",
        melodyAlias = "C6",
        sentCardJson = "{}",
        receivedCardJson = "{}",
        exchangedAt = 1L,
        syncState = "PENDING",
        expiresAt = null,
        payloadHash = "hash",
        recordSignature = "signature",
    )
}
