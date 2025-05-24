package com.example.purrytify.model.analytics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TopArtistStats(
    val name: String,
    val playCount: Int,
    val imageUrl: String? = null
) : Parcelable