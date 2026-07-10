package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_exchange_local")
data class OfflineExchangeEntity(
    @PrimaryKey val id: String,
    val localSessionId: String,
    val peerDisplayAlias: String,
    val trackTitle: String,
    val trackArtist: String,
    val melodyAlias: String,
    val exchangedAt: Long,
    val syncState: String,
    val expiresAt: Long?
)

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val requestId: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)

@Dao
interface OfflineExchangeDao {
    @Query("SELECT * FROM offline_exchange_local ORDER BY exchangedAt DESC")
    fun observeAll(): Flow<List<OfflineExchangeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchange: OfflineExchangeEntity)

    @Update
    suspend fun update(exchange: OfflineExchangeEntity)

    @Query("DELETE FROM offline_exchange_local WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SyncOutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [OfflineExchangeEntity::class, SyncOutboxEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun offlineExchangeDao(): OfflineExchangeDao
    abstract fun syncOutboxDao(): SyncOutboxDao

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
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
