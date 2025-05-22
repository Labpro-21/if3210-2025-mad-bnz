package com.example.purrytify.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sound_capsule")
data class SoundCapsuleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val month: Int,
    val year: Int,
    val totalTimeListened: Long, // in minutes
    val dailyAverage: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "top_artists")
data class TopArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capsuleId: String,
    val artistId: String,
    val rank: Int,
    val playCount: Int
)

@Entity(tableName = "top_songs")
data class TopSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capsuleId: String,
    val songId: String,
    val rank: Int,
    val playCount: Int
)

@Entity(tableName = "song_streaks")
data class SongStreakEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capsuleId: String,
    val songId: String,
    val startDate: Long,
    val endDate: Long,
    val daysCount: Int
)