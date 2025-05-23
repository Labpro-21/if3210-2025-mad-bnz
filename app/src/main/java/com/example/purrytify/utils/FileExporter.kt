package com.example.purrytify.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.purrytify.model.analytics.SoundCapsule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun exportToCsv(capsule: SoundCapsule, fileName: String): Uri {
        val csvFile = File(context.cacheDir, fileName)
        csvFile.writer().use { writer ->
            writer.write("Monthly Analytics Report\n")
            writer.write("Month: ${capsule.month}/${capsule.year}\n")
            writer.write("Total Time Listened: ${capsule.timeListened} minutes\n\n")
            
            writer.write("Top Artists:\n")
            capsule.topArtists.forEach { artist ->
                writer.write("${artist.name},${artist.playCount}\n")
            }
            
            writer.write("\nTop Songs:\n")
            capsule.topSongs.forEach { song ->
                writer.write("${song.title},${song.artist},${song.playCount}\n")
            }
        }
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            csvFile
        )
    }
}