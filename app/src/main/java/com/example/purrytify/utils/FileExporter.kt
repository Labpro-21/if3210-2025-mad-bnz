package com.example.purrytify.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun exportToCsv(capsule: SoundCapsule, filename: String): Uri {
        val csvContent = buildString {
            appendLine("Sound Capsule Analytics")
            appendLine()
            appendLine("Time Listened: ${capsule.timeListened} minutes")
            appendLine()
            appendLine("Top Artists:")
            capsule.topArtists.forEach { artist ->
                appendLine("${artist.name},${artist.playCount} plays")
            }
            appendLine()
            appendLine("Top Songs:")
            capsule.topSongs.forEach { song ->
                appendLine("${song.title},${song.artist},${song.playCount} plays")
            }
            appendLine()
            appendLine("Streaks:")
            capsule.streaks.forEach { streak ->
                appendLine("${streak.songTitle},${streak.daysCount} days,${streak.startDate} - ${streak.endDate}")
            }
        }

        val file = File(context.cacheDir, filename)
        file.writeText(csvContent)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}