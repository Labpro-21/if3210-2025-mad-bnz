package com.example.purrytify.model.analytics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongStreakStats(
    val songId: String,
    val songTitle: String,
    val image: String,
    val artist: String,
    val daysCount: Int,
    val startDate: Long,
    val endDate: Long
) : Parcelable