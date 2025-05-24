package com.example.purrytify.repository

import android.util.Log
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.Song
import com.example.purrytify.network.ApiService
import com.example.purrytify.room.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files.exists
import javax.inject.Inject
import android.content.Context
import com.example.purrytify.utils.CountryUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.flowOn
import javax.inject.Singleton

import kotlinx.coroutines.withContext

@Singleton
class SongRepository @Inject constructor(
    private val songDao: SongDao,
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) {
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()
    fun getRecentlyPlayed(): Flow<List<Song>> = songDao.getRecentlyPlayed()
    fun getNewSongs(): Flow<List<Song>> = songDao.getNewSongs()

    fun getOfflineSongs(): Flow<List<Song>> = songDao.getOfflineSongs()

    fun getGlobalTopSongs(): Flow<List<Song>> = songDao.getGlobalTopSongs()

//    val countries = CountryUtils.getCountryCode(tokenManager.getUserCountry().toString())
    fun getCountryTopSongs(): Flow<List<Song>> = songDao.getCountryTopSongs(tokenManager.getUserCountry().toString())
    fun getDownloadedSongs(): Flow<List<Song>> = songDao.getDownloadedSongs()

    suspend fun addOrUpdateOnlineSongs(songs: List<Song>) {
        withContext(Dispatchers.IO) {
            songs.forEach { song ->
                songDao.insertSong(song)
            }
        }
    }

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

    suspend fun addLocalSong(song: Song): Long? {
        return withContext(Dispatchers.IO) {
            songDao.insertOfflineSong(song)
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

    suspend fun downloadSong(song: Song): Flow<Int> = flow {
        emit(0) // Initial progress

        // Create directories if they don't exist
        val musicDir = File(context.filesDir, "music").apply {
            if (!exists()) mkdir()
        }
        val artworkDir = File(context.filesDir, "artwork").apply {
            if (!exists()) mkdir()
        }

        // Define files
        val audioFile = File(musicDir, "${song.id}.mp3")
        val artworkFile = File(artworkDir, "${song.id}.jpg")

        try {
            // Download audio file
            val connection = URL(song.path).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val fileSize = connection.contentLength
            var totalBytesRead = 0L

            connection.inputStream.buffered().use { input ->
                FileOutputStream(audioFile).buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = ((totalBytesRead * 100) / fileSize).toInt()
                        emit(progress) // Emit directly in IO context
                    }
                }
            }

            // Download artwork if available
            song.coverUrl?.let { coverUrl ->
                try {
                    val artworkConnection = URL(coverUrl).openConnection() as HttpURLConnection
                    artworkConnection.inputStream.use { input ->
                        FileOutputStream(artworkFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SongRepository", "Error downloading artwork", e)
                }
            }

            // Create downloaded song with local paths
            val downloadedSong = song.copy(
                path = audioFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                coverUrl = if (artworkFile.exists()) artworkFile.absolutePath else song.coverUrl,
                isDownloaded = true,
                isLocal = true,
            )

            // Save to database
            songDao.insertDownloadedSong(downloadedSong)
            emit(100) // Final progress

        } catch (e: Exception) {
            Log.e("SongRepository", "Download error", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun insertSong(song: Song) {
        withContext(Dispatchers.IO) {
            songDao.insertSong(song)
        }
    }
}