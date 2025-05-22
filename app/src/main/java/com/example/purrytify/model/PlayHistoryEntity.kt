package com.example.purrytify.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Locale

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val userId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val duration: Long, // Duration played in milliseconds
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
)