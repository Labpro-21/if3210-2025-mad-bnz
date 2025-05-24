package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.purrytify.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long  // Return inserted row ID

//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertOfflineSong(song: Song): Long {
//        return insertSong(song.copy(isLocal = true))
//    }

//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertDownloadedSong(song: Song): Long {
//        return insertSong(song.copy(isDownloaded = true))
//    }

    @Query("SELECT * FROM songs WHERE title = :title AND artist = :artist AND (isLocal = 1 OR isDownloaded = 1) LIMIT 1")
    suspend fun checkDuplicateOfflineSong(title: String, artist: String): Song?


    @Transaction
    suspend fun insertDownloadedSong(song: Song): Long? {
        val existingSong = checkDuplicateOfflineSong(song.title, song.artist)
        return if (existingSong != null) {
            null
        } else {
            insertSong(song.copy(isDownloaded = true))
        }
    }


    @Transaction
    suspend fun insertOfflineSong(song: Song): Long? {
        val existingSong = checkDuplicateOfflineSong(song.title, song.artist)
        return if (existingSong != null) {
            null
        } else {
            insertSong(song.copy(isLocal = true))
        }
    }

    @Update
    suspend fun updateSong(song: Song): Int  // Return number of rows updated

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun getAllSongs(): Flow<List<Song>>  // Flow is fine as-is

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY createdAt DESC")
    fun getLikedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY lastPlayed DESC LIMIT 5")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY createdAt DESC ")
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

    @Query("SELECT * FROM songs WHERE country = :countryCode AND (isLocal = 0 AND isDownloaded = 0) LIMIT 10")
    fun getCountryTopSongs(countryCode: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    fun getDownloadedSongs(): Flow<List<Song>>
}
