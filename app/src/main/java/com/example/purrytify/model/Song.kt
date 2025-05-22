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
    val duration: Long, // Store duration in milliseconds
    val path: String, // Local file path or URL for online songs
    val coverUrl: String? = null,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0,
    // Online song specific fields
    val rank: Int? = null,
    val country: String? = null,
    val isDownloaded: Boolean = false,
    val originalDuration: String? = null // Store original duration format for online songs
) : Serializable