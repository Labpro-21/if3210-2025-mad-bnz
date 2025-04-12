package com.example.purrytify.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val coverUrl: String? = null,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0
) : Serializable