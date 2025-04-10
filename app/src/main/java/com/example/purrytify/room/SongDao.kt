package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.purrytify.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Update
    suspend fun updateSong(song: Song)

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY createdAt DESC")
    fun getLikedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY lastPlayed DESC LIMIT 10")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC LIMIT 10")
    fun getNewSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET lastPlayed = :timestamp WHERE id = :songId")
    fun updateLastPlayed(songId: String, timestamp: Long): Int

    @Query("DELETE FROM songs WHERE id = :songId")
    fun deleteSong(songId: String): Int
}