package com.example.purrytify.room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.model.Song
import com.example.purrytify.model.SongStreakEntity
import com.example.purrytify.model.SoundCapsuleEntity
import com.example.purrytify.model.TopArtistEntity
import com.example.purrytify.model.TopSongEntity

@Database(
    entities = [Song::class,
                PlayHistoryEntity::class,
                SoundCapsuleEntity::class,
                TopArtistEntity::class,
                TopSongEntity::class,
                SongStreakEntity::class
               ],
    version = 4, // Increment version number from 2 to 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun soundCapsuleDao(): SoundCapsuleDao
    companion object {
        const val DATABASE_NAME = "purrytify_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN lastPlayed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create temporary table with new schema
                database.execSQL("""
                    CREATE TABLE songs_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        path TEXT NOT NULL,
                        coverUrl TEXT,
                        isLiked INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastPlayed INTEGER NOT NULL DEFAULT 0,
                        rank INTEGER,
                        country TEXT,
                        isDownloaded INTEGER NOT NULL DEFAULT 0,
                        originalDuration TEXT
                    )
                """)
                
                // Copy data from old table to new table
                database.execSQL("""
                    INSERT INTO songs_new (
                        id, title, artist, duration, path, coverUrl, 
                        isLiked, isLocal, createdAt, updatedAt, lastPlayed
                    )
                    SELECT id, title, artist, duration, path, coverUrl, 
                           isLiked, isLocal, createdAt, updatedAt, lastPlayed
                    FROM songs
                """)
                
                // Remove old table
                database.execSQL("DROP TABLE songs")
                
                // Rename new table to original name
                database.execSQL("ALTER TABLE songs_new RENAME TO songs")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE songs SET isLocal = 1 WHERE country IS NULL")
            }
        }
    }
}