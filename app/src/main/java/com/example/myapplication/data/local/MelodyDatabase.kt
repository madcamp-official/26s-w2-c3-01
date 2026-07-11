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
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    version = 2,
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
                    .addCallback(seedCallback)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedDemoCandidates()
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                seedDemoCandidates()
            }
        }

        private fun seedDemoCandidates() {
            instance?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.melodyAliasCandidateDao()
                    if (dao.count() == 0) {
                        dao.insertAll(demoMelodyAliasCandidates)
                    }
                }
            }
        }

        private val demoMelodyAliasCandidates = listOf(
            MelodyAliasCandidateEntity(
                id = "mint-ring",
                name = "Mint Ring",
                mood = "밝음",
                tone = "전자음",
                tempo = 132,
                energy = "경쾌",
                notesCsv = "C6,E6,G6,C7",
                rhythmCsv = "105,105,105,230",
                toneJsPreset = "sine bell, short attack, soft delay",
                melodyId = "MB-C6E6G6C7-BELL"
            ),
            MelodyAliasCandidateEntity(
                id = "dream-tap",
                name = "Dream Tap",
                mood = "몽환",
                tone = "전자음",
                tempo = 108,
                energy = "부드러움",
                notesCsv = "A5,C6,E6,B5,G5",
                rhythmCsv = "120,120,220,120,260",
                toneJsPreset = "glass sine8, long release, airy reverb",
                melodyId = "MB-A5C6E6B5G5-GLASS"
            ),
            MelodyAliasCandidateEntity(
                id = "pixel-blink",
                name = "Pixel Blink",
                mood = "귀여움",
                tone = "전자음",
                tempo = 152,
                energy = "통통",
                notesCsv = "G5,C6,D6,G6",
                rhythmCsv = "80,120,80,210",
                toneJsPreset = "square wave, crisp envelope",
                melodyId = "MB-G5C6D6G6-PIX"
            ),
            MelodyAliasCandidateEntity(
                id = "soft-signal",
                name = "Soft Signal",
                mood = "차분",
                tone = "피아노",
                tempo = 96,
                energy = "잔잔",
                notesCsv = "F5,A5,C6,A5",
                rhythmCsv = "140,220,140,340",
                toneJsPreset = "triangle warm keys, low velocity",
                melodyId = "MB-F5A5C6A5-SOFT"
            ),
            MelodyAliasCandidateEntity(
                id = "lucky-chime",
                name = "Lucky Chime",
                mood = "설렘",
                tone = "벨",
                tempo = 124,
                energy = "반짝",
                notesCsv = "D6,F#6,A6,E6,A6",
                rhythmCsv = "105,105,105,105,240",
                toneJsPreset = "pluck bell, bright transient",
                melodyId = "MB-D6FS6A6E6A6-LUCK"
            ),
            MelodyAliasCandidateEntity(
                id = "piano-hello",
                name = "Piano Hello",
                mood = "밝음",
                tone = "피아노",
                tempo = 118,
                energy = "명확",
                notesCsv = "E5,G5,C6,E6",
                rhythmCsv = "120,120,180,260",
                toneJsPreset = "warm piano pluck, clean release",
                melodyId = "MB-E5G5C6E6-PIANO"
            ),
            MelodyAliasCandidateEntity(
                id = "guitar-nod",
                name = "Guitar Nod",
                mood = "차분",
                tone = "기타",
                tempo = 104,
                energy = "따뜻",
                notesCsv = "D5,F5,A5,C6",
                rhythmCsv = "150,150,220,280",
                toneJsPreset = "nylon pluck, soft body",
                melodyId = "MB-D5F5A5C6-GTR"
            ),
            MelodyAliasCandidateEntity(
                id = "neon-step",
                name = "Neon Step",
                mood = "에너지",
                tone = "전자음",
                tempo = 148,
                energy = "빠름",
                notesCsv = "C6,D6,F6,G6,C7",
                rhythmCsv = "85,85,85,85,220",
                toneJsPreset = "saw lead, tiny delay",
                melodyId = "MB-C6D6F6G6C7-NEON"
            )
        )
    }
}
