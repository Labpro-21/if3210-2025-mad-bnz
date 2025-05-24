package com.example.purrytify.model.analytics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TopSongStats(
    val title: String,
    val artist: String,
    val playCount: Int,
    val imageUrl: String? = null
) : Parcelable