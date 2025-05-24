package com.example.purrytify.model.analytics
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SoundCapsule(
    val month: String,
    val year: Int,
    val timeListened: Long, // in minutes
    val topArtists: List<TopArtistStats>,
    val topSongs: List<TopSongStats>,
    val streaks: List<SongStreakStats>
) : Parcelable