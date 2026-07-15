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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "melody_alias_candidates")
data class MelodyAliasCandidateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tone: String,
    val tempo: Int,
    val energy: String,
    val notesCsv: String,
    val rhythmCsv: String,
    val toneJsPreset: String,
    val melodyId: String
)

@Dao
interface MelodyAliasCandidateDao {
    @Query("SELECT * FROM melody_alias_candidates ORDER BY tone, name")
    fun observeAll(): Flow<List<MelodyAliasCandidateEntity>>

    @Query("SELECT COUNT(*) FROM melody_alias_candidates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<MelodyAliasCandidateEntity>)
}

@Database(
    entities = [
        MelodyAliasCandidateEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class MelodyDatabase : RoomDatabase() {
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS offline_exchange_local")
                db.execSQL("DROP TABLE IF EXISTS sync_outbox")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS melody_alias_candidates_new (
                      id TEXT NOT NULL PRIMARY KEY,
                      name TEXT NOT NULL,
                      tone TEXT NOT NULL,
                      tempo INTEGER NOT NULL,
                      energy TEXT NOT NULL,
                      notesCsv TEXT NOT NULL,
                      rhythmCsv TEXT NOT NULL,
                      toneJsPreset TEXT NOT NULL,
                      melodyId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO melody_alias_candidates_new(
                      id,name,tone,tempo,energy,notesCsv,rhythmCsv,toneJsPreset,melodyId
                    )
                    SELECT id,name,tone,tempo,energy,notesCsv,rhythmCsv,toneJsPreset,melodyId
                    FROM melody_alias_candidates
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE melody_alias_candidates")
                db.execSQL("ALTER TABLE melody_alias_candidates_new RENAME TO melody_alias_candidates")
            }
        }

    }
}
