package com.example.purrytify.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.purrytify.model.analytics.SoundCapsule
import com.opencsv.CSVWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
//    fun exportToCsv(capsule: SoundCapsule, fileName: String): Uri {
//        val csvFile = File(context.cacheDir, fileName)
//        csvFile.writer().use { writer ->
//            writer.write("Monthly Analytics Report\n")
//            writer.write("Month: ${capsule.month}/${capsule.year}\n")
//            writer.write("Total Time Listened: ${capsule.timeListened} minutes\n\n")
//
//            writer.write("Top Artists:\t Total Play:\n")
//            capsule.topArtists.forEach { artist ->
//                writer.write("${artist.name},${artist.playCount}\n")
//            }
//
//            writer.write("\nTop Songs:\n")
//            capsule.topSongs.forEach { song ->
//                writer.write("${song.title},${song.artist},${song.playCount}\n")
//            }
//        }
//
//        return FileProvider.getUriForFile(
//            context,
//            "${context.packageName}.provider",
//            csvFile
//        )
//    }

    fun exportToCsv(capsule: SoundCapsule, fileName: String): Uri {
        // Create documents directory if it doesn't exist
        val documentsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Purrytify")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val csvFile = File(documentsDir, fileName)
        CSVWriter(FileWriter(csvFile)).use { writer ->
            // Write header section
            writer.writeNext(arrayOf("Monthly Analytics Report"))
            writer.writeNext(arrayOf("Month", "${capsule.month}/${capsule.year}"))
            writer.writeNext(arrayOf("Total Time Listened (minutes)", capsule.timeListened.toString()))
            writer.writeNext(arrayOf()) // Empty line

            // Write top artists section
            writer.writeNext(arrayOf("Top Artists", "Total Play Count"))
            capsule.topArtists.forEach { artist ->
                writer.writeNext(arrayOf(
                    artist.name,
                    artist.playCount.toString()
                ))
            }
            writer.writeNext(arrayOf()) // Empty line

            // Write top songs section
            writer.writeNext(arrayOf("Top Songs", "Artist", "Play Count"))
            capsule.topSongs.forEach { song ->
                writer.writeNext(arrayOf(
                    song.title,
                    song.artist,
                    song.playCount.toString()
                ))
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            csvFile
        )
    }

}