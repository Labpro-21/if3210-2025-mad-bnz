package com.example.purrytify.model.analytics

data class TopSongStats(
    val title: String,
    val artist: String,
    val playCount: Int,
    val imageUrl: String? = null
)