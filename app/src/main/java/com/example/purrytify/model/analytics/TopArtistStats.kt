package com.example.purrytify.model.analytics

data class TopArtistStats(
    val name: String,
    val playCount: Int,
    val imageUrl: String? = null
)