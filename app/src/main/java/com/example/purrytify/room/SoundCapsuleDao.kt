package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.purrytify.model.SoundCapsuleEntity
import com.example.purrytify.model.TopArtistEntity
import com.example.purrytify.model.TopSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundCapsuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapsule(capsule: SoundCapsuleEntity)

    @Query("""
        SELECT * FROM sound_capsule 
        WHERE userId = :userId 
        AND month = :month 
        AND year = :year 
        LIMIT 1
    """)
    fun getCapsule(userId: String, month: Int, year: Int): Flow<SoundCapsuleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopArtists(artists: List<TopArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopSongs(songs: List<TopSongEntity>)

    @Transaction
    @Query("DELETE FROM sound_capsule WHERE userId = :userId AND month = :month AND year = :year")
    suspend fun deleteCapsule(userId: String, month: Int, year: Int)
}