package com.example.purrytify.download

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.Glide
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
) {
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    suspend fun downloadSong(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        try {
            // Create local file
            val fileName = "${song.id}.mp3"
            val file = File(context.filesDir, fileName)

            // Download audio file
            val connection = URL(song.path).openConnection() as HttpURLConnection
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(file)
            
            val buffer = ByteArray(1024)
            var bytesRead: Int
            var totalBytesRead = 0L
            val fileSize = connection.contentLength

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = ((totalBytesRead * 100) / fileSize).toInt()
                _downloadProgress.update { it + (song.id to progress) }
            }

            outputStream.close()
            inputStream.close()

            // Download artwork if available
            song.coverUrl?.let { artworkUrl ->
                val artworkFile = File(context.filesDir, "${song.id}_artwork.jpg")
                downloadArtwork(artworkUrl, artworkFile)
            }

            // Convert to local Song and save
            val localSong = Song(
                id = song.id,
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                path = file.absolutePath,
                coverUrl = song.coverUrl,
                isLocal = false,
                createdAt = System.currentTimeMillis()
            )

            songRepository.addLocalSong(localSong)
            
            _downloadProgress.update { it - song.id }
            Result.success(localSong)
        } catch (e: Exception) {
            _downloadProgress.update { it - song.id }
            Result.failure(e)
        }
    }

    private suspend fun downloadArtwork(url: String, file: File) = withContext(Dispatchers.IO) {
        try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .submit()
                .get()
                .compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(file))
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error downloading artwork", e)
        }
    }

    private fun parseDuration(duration: String): Long {
        val parts = duration.split(":")
        return when (parts.size) {
            2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
            else -> 0
        }
    }
}