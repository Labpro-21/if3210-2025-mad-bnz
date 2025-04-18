package com.example.purrytify.repository

import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.Song
import com.example.purrytify.network.ApiService
import com.example.purrytify.room.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SongRepository @Inject constructor(
    private val songDao: SongDao,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()
    fun getRecentlyPlayed(): Flow<List<Song>> = songDao.getRecentlyPlayed()
    fun getNewSongs(): Flow<List<Song>> = songDao.getNewSongs()

    suspend fun updateSong(song: Song): Int {
        return withContext(Dispatchers.IO) {
            songDao.updateSong(song)
        }
    }

    suspend fun updateLastPlayed(songId: String, timestamp: Long): Int {
        return withContext(Dispatchers.IO) {
            songDao.updateLastPlayed(songId, timestamp)
        }
    }

    suspend fun addLocalSong(song: Song): Long {
        return withContext(Dispatchers.IO) {
            songDao.insertSong(song)
        }
    }

    suspend fun deleteSong(songId: String): Int {
        return withContext(Dispatchers.IO) {
            songDao.deleteSong(songId)
        }
    }
    suspend fun getLikedSongs(): Flow<List<Song>> {
        return withContext(Dispatchers.IO){ songDao.getLikedSongs()}
    }

    suspend fun getSongById(id: String): Song? {
        return withContext(Dispatchers.IO){songDao.getSongById(id)}
    }
    fun getPlayedSongs(): Flow<List<Song>> {
        return songDao.getPlayedSongs()
    }
    fun getRecentlyAddedSongs(): Flow<List<Song>> {
        // Calculate timestamp for a day ago
        val thirtyDaysAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L) //open for modif
        return songDao.getRecentlyAddedSongs(thirtyDaysAgo)
    }
}