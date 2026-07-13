package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "offline_exchange_local",
    primaryKeys = ["ownerUserId", "id"],
    indices = [Index(value = ["ownerUserId", "exchangedAt"], name = "offline_exchange_owner_time_idx")],
)
data class OfflineExchangeEntity(
    val id: String,
    val ownerUserId: String,
    val localSessionId: String,
    val credentialId: String?,
    val peerCredentialId: String?,
    val peerDisplayAlias: String,
    val trackTitle: String,
    val trackArtist: String,
    val melodyAlias: String,
    val sentCardJson: String,
    val receivedCardJson: String,
    val exchangedAt: Long,
    val syncState: String,
    val expiresAt: Long?,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val payloadHash: String,
    val protocolVersion: Int = 1,
    val recordSignature: String,
)

@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["ownerUserId", "id"],
    indices = [Index(value = ["ownerUserId", "createdAt"], name = "sync_outbox_owner_time_idx")],
)
data class SyncOutboxEntity(
    val id: String,
    val ownerUserId: String,
    val kind: String,
    val requestId: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "melody_alias_candidates")
data class MelodyAliasCandidateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mood: String,
    val tone: String,
    val tempo: Int,
    val energy: String,
    val notesCsv: String,
    val rhythmCsv: String,
    val toneJsPreset: String,
    val melodyId: String
)

@Dao
interface OfflineExchangeDao {
    @Query("SELECT * FROM offline_exchange_local WHERE ownerUserId = :ownerUserId ORDER BY exchangedAt DESC")
    fun observeForOwner(ownerUserId: String): Flow<List<OfflineExchangeEntity>>

    @Query("SELECT * FROM offline_exchange_local WHERE ownerUserId = :ownerUserId AND syncState IN ('PENDING', 'UPLOADING', 'FAILED') ORDER BY exchangedAt ASC")
    suspend fun pendingForOwner(ownerUserId: String): List<OfflineExchangeEntity>

    @Query("SELECT * FROM offline_exchange_local WHERE id = :exchangeId AND ownerUserId = :ownerUserId LIMIT 1")
    suspend fun find(ownerUserId: String, exchangeId: String): OfflineExchangeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchange: OfflineExchangeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exchanges: List<OfflineExchangeEntity>)

    @Update
    suspend fun update(exchange: OfflineExchangeEntity)

    @Query("UPDATE offline_exchange_local SET syncState = :state, retryCount = :retryCount, lastError = :lastError WHERE id = :exchangeId AND ownerUserId = :ownerUserId")
    suspend fun updateSyncState(
        ownerUserId: String,
        exchangeId: String,
        state: String,
        retryCount: Int,
        lastError: String?,
    )

    @Query("DELETE FROM offline_exchange_local WHERE id = :exchangeId AND ownerUserId = :ownerUserId")
    suspend fun delete(ownerUserId: String, exchangeId: String)

    @Query("DELETE FROM offline_exchange_local WHERE ownerUserId = :ownerUserId AND expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(ownerUserId: String, now: Long)
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    fun observeForOwner(ownerUserId: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    suspend fun pendingForOwner(ownerUserId: String): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun delete(ownerUserId: String, id: String)
}

@Dao
interface MelodyAliasCandidateDao {
    @Query("SELECT * FROM melody_alias_candidates ORDER BY mood, tone, name")
    fun observeAll(): Flow<List<MelodyAliasCandidateEntity>>

    @Query("SELECT COUNT(*) FROM melody_alias_candidates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<MelodyAliasCandidateEntity>)
}

@Database(
    entities = [
        OfflineExchangeEntity::class,
        SyncOutboxEntity::class,
        MelodyAliasCandidateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun offlineExchangeDao(): OfflineExchangeDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun melodyAliasCandidateDao(): MelodyAliasCandidateDao

    companion object {
        @Volatile
        private var instance: MelodyDatabase? = null

        fun getInstance(context: Context): MelodyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MelodyDatabase::class.java,
                    "melody-bubble-local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 only contained anonymous demo rows. Keep them quarantined under a
                // non-account owner instead of attributing them to whichever user logs in next.
                db.execSQL("""CREATE TABLE offline_exchange_local_new (
                    id TEXT NOT NULL, ownerUserId TEXT NOT NULL, localSessionId TEXT NOT NULL,
                    credentialId TEXT, peerCredentialId TEXT, peerDisplayAlias TEXT NOT NULL,
                    trackTitle TEXT NOT NULL, trackArtist TEXT NOT NULL, melodyAlias TEXT NOT NULL,
                    sentCardJson TEXT NOT NULL, receivedCardJson TEXT NOT NULL, exchangedAt INTEGER NOT NULL,
                    syncState TEXT NOT NULL, expiresAt INTEGER, retryCount INTEGER NOT NULL,
                    lastError TEXT, payloadHash TEXT NOT NULL, protocolVersion INTEGER NOT NULL,
                    recordSignature TEXT NOT NULL, PRIMARY KEY(ownerUserId,id))""")
                db.execSQL("""INSERT INTO offline_exchange_local_new
                    (id,ownerUserId,localSessionId,credentialId,peerCredentialId,peerDisplayAlias,
                     trackTitle,trackArtist,melodyAlias,sentCardJson,receivedCardJson,exchangedAt,
                     syncState,expiresAt,retryCount,lastError,payloadHash,protocolVersion,recordSignature)
                    SELECT id,'__legacy__',localSessionId,NULL,NULL,peerDisplayAlias,trackTitle,trackArtist,
                     melodyAlias,'{}','{}',exchangedAt,syncState,expiresAt,0,NULL,'',1,''
                    FROM offline_exchange_local""")
                db.execSQL("DROP TABLE offline_exchange_local")
                db.execSQL("ALTER TABLE offline_exchange_local_new RENAME TO offline_exchange_local")

                db.execSQL("""CREATE TABLE sync_outbox_new (
                    id TEXT NOT NULL, ownerUserId TEXT NOT NULL, kind TEXT NOT NULL, requestId TEXT NOT NULL,
                    payloadJson TEXT NOT NULL, createdAt INTEGER NOT NULL, retryCount INTEGER NOT NULL,
                    lastError TEXT, PRIMARY KEY(ownerUserId,id))""")
                db.execSQL("""INSERT INTO sync_outbox_new
                    (id,ownerUserId,kind,requestId,payloadJson,createdAt,retryCount,lastError)
                    SELECT id,'__legacy__',kind,requestId,payloadJson,createdAt,retryCount,lastError FROM sync_outbox""")
                db.execSQL("DROP TABLE sync_outbox")
                db.execSQL("ALTER TABLE sync_outbox_new RENAME TO sync_outbox")
                db.execSQL("CREATE INDEX IF NOT EXISTS offline_exchange_owner_time_idx ON offline_exchange_local(ownerUserId, exchangedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS sync_outbox_owner_time_idx ON sync_outbox(ownerUserId, createdAt)")
            }
        }

    }
}
