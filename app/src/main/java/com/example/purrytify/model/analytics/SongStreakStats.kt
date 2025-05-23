package com.example.purrytify.model.analytics


data class SongStreakStats(
    val songId: String,
    val songTitle: String,
    val image: String,
    val artist: String,
    val daysCount: Int,
    val startDate: Long,
    val endDate: Long
)