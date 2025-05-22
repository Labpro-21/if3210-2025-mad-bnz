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
    suspend fun insertSong(song: Song): Long  // Return inserted row ID

    @Update
    suspend fun updateSong(song: Song): Int  // Return number of rows updated

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun getAllSongs(): Flow<List<Song>>  // Flow is fine as-is

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY createdAt DESC")
    fun getLikedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY lastPlayed DESC LIMIT 10")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC LIMIT 10")
    fun getNewSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET lastPlayed = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayed(songId: String, timestamp: Long): Int

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: String): Int

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song

    @Query("SELECT * FROM songs WHERE lastPlayed > 0")
    fun getPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE createdAt > :timestamp ORDER BY createdAt DESC LIMIT 10")
    fun getRecentlyAddedSongs(timestamp: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isLocal = 1 OR isDownloaded = 1")
    fun getOfflineSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE country = 'GLOBAL' AND (isLocal = 0 AND isDownloaded = 0)")
    fun getGlobalTopSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE country != 'GLOBAL' AND (isLocal = 0 AND isDownloaded = 0)")
    fun getCountryTopSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    fun getDownloadedSongs(): Flow<List<Song>>
}