package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.purrytify.model.SongStreakEntity
import com.example.purrytify.model.SoundCapsuleEntity
import com.example.purrytify.model.TopArtistEntity
import com.example.purrytify.model.TopSongEntity

@Dao
interface SoundCapsuleDao {
    @Insert
    suspend fun insertCapsule(capsule: SoundCapsuleEntity)

    @Insert
    suspend fun insertTopArtists(artists: List<TopArtistEntity>)

    @Insert
    suspend fun insertTopSongs(songs: List<TopSongEntity>)

    @Insert
    suspend fun insertStreak(streak: SongStreakEntity)

    @Transaction
    @Query("""
        SELECT * FROM sound_capsule 
        WHERE userId = :userId 
        AND month = :month 
        AND year = :year
    """)
    suspend fun getCapsuleWithDetails(userId: String, month: Int, year: Int): SoundCapsuleWithDetails
}