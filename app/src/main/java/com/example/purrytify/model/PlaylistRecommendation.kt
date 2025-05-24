package com.example.purrytify.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlaylistRecommendation(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val songs: List<Song>
) : Parcelable