package com.example.purrytify.repository

import android.content.Context
import android.util.Log
import com.example.purrytify.model.Song
import com.example.purrytify.model.SongResponse
import com.example.purrytify.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch

class OnlineSongRepository @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
) {
    suspend fun getGlobalTopSongs(): Flow<List<Song>> = flow {
        try {
            val response = apiService.getGlobalTopSongs()
            val songs = response.body()?.map { songResponse ->
                mapResponseToSong(songResponse)
            } ?: emptyList()
            emit(songs)
        } catch (e: Exception) {
            Log.e("OnlineSongRepository", "Error fetching global songs", e)
            throw e // Let the caller handle the error
        }
    }.catch { e ->
        Log.e("OnlineSongRepository", "Error in flow", e)
        emit(emptyList())
    }

    suspend fun getCountryTopSongs(countryCode: String): Flow<List<Song>> = flow {
        try {
            val response = apiService.getCountryTopSongs(countryCode)
            val songs = response.body()?.map { songResponse ->
                mapResponseToSong(songResponse)
            } ?: emptyList()
            emit(songs)
        } catch (e: Exception) {
            Log.e("OnlineSongRepository", "Error fetching country songs", e)
            throw e
        }
    }.catch { e ->
        Log.e("OnlineSongRepository", "Error in flow", e)
        emit(emptyList())
    }

    suspend fun downloadSong(song: Song): Flow<Int> = flow {
        try {
            emit(0)

            val musicDir = File(context.filesDir, "music").apply {
                if (!exists()) mkdirs()
            }

            // Download audio file
            val audioFile = File(musicDir, "${song.id}.mp3")
            val connection = URL(song.path).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val inputStream = connection.inputStream.buffered()
            val outputStream = FileOutputStream(audioFile).buffered()

            val fileSize = connection.contentLength
            var bytesRead: Int
            var totalBytesRead = 0L
            val buffer = ByteArray(8192)

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = ((totalBytesRead * 100) / fileSize).toInt()
                emit(progress)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Download artwork
            val artworkDir = File(context.filesDir, "artwork").apply {
                if (!exists()) mkdirs()
            }
            val artworkFile = File(artworkDir, "${song.id}.jpg")

            try {
                val artworkConnection = URL(song.coverUrl).openConnection() as HttpURLConnection
                artworkConnection.inputStream.use { input ->
                    FileOutputStream(artworkFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("OnlineSongRepository", "Error downloading artwork", e)
            }

            // Save as downloaded song
            val downloadedSong = song.copy(
                path = audioFile.absolutePath,
                coverUrl = artworkFile.absolutePath,
                isDownloaded = true
            )
            songRepository.addLocalSong(downloadedSong)
            emit(100)

        } catch (e: Exception) {
            Log.e("OnlineSongRepository", "Download error", e)
            throw e
        }
    }

    private fun mapResponseToSong(songResponse: SongResponse): Song {
        return Song(
            id = songResponse.id,
            title = songResponse.title,
            artist = songResponse.artist,
            duration = parseDuration(songResponse.duration),
            path = songResponse.url,
            coverUrl = songResponse.artwork,
            isLocal = false,
            isDownloaded = false,
            rank = songResponse.rank,
            country = songResponse.country,
            originalDuration = songResponse.duration,
            createdAt = parseDate(songResponse.createdAt),
            updatedAt = parseDate(songResponse.updatedAt)
        )
    }

    private fun parseDuration(duration: String): Long {
        return try {
            val parts = duration.split(":")
            (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}