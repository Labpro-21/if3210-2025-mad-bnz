package com.example.purrytify.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.purrytify.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FilePickerHelper(private val fragment: Fragment, private var onSongImported: (Song) -> Unit) {

    private val audioPickerLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                CoroutineScope(Dispatchers.IO).launch{
                    val contentResolver = fragment.requireContext().contentResolver
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val song = extractMetadata(uri)
                    withContext(Dispatchers.Main) {
                        onSongImported(song)
                    }
                }

            }
        }
    }

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openAudioPicker()
        } else {
            fragment.requireContext().showToast("Storage permission denied")
        }
    }

    fun setCallback(callback: (Song) -> Unit) {
        this.onSongImported = callback
    }

    fun getCallback(): (Song) -> Unit {
        return onSongImported
    }

    fun pickAudioFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        audioPickerLauncher.launch(intent)
    }

    private fun extractMetadata(uri: Uri): Song {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(fragment.requireContext(), uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val albumArt = retriever.embeddedPicture
            var albumArtPath = ""
            if (albumArt != null) {

                val albumArtFile = File(fragment.requireContext().filesDir, "album_art_${UUID.randomUUID()}.jpg")
                albumArtFile.writeBytes(albumArt)
                albumArtPath = albumArtFile.absolutePath
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L



            fragment.requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            return Song(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                path = uri.toString(),
                coverUrl = albumArtPath,
                isLiked = false,
                isDownloaded = false,
                isLocal = true,
                createdAt = System.currentTimeMillis(),
                duration = duration,
                lastPlayed = 0
            )
        } finally {
            retriever.release()
        }
    }
}