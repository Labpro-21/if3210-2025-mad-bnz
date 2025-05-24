package com.example.purrytify.repository

import android.util.Log
import com.example.purrytify.model.PlaylistRecommendation
import com.example.purrytify.model.Song
import com.example.purrytify.model.analytics.TopArtistStats
import com.example.purrytify.room.AnalyticsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepository @Inject constructor(
    private val songRepository: SongRepository,
    private val analyticsDao: AnalyticsDao
) {

    private suspend fun getTopArtists(): Flow<List<TopArtistStats>> {
        return analyticsDao.getTopArtists(
            userId = "",
            yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        )
    }

    private suspend fun getSongsByArtists(artists: List<String>): Flow<List<Song>> = flow {
        val songs = analyticsDao.getSongsByArtists(artists)
        emit(songs)
    }

    suspend fun getDailyRecommendations(): Flow<List<PlaylistRecommendation>> = flow {
        val recommendations = mutableListOf<PlaylistRecommendation>()
        
        try {
            // Get liked songs mix
            songRepository.getLikedSongs().first().let { likedSongs ->
                if (likedSongs.isNotEmpty()) {
                    recommendations.add(
                        PlaylistRecommendation(
                            id = "liked_mix",
                            name = "Liked Songs Mix",
                            description = "Based on your liked songs",
                            imageUrl = likedSongs.firstOrNull()?.coverUrl,
                            songs = likedSongs.shuffled().take(20)
                        )
                    )
                }
            }

            // Get most played artists mix
            getTopArtists().first().let { topArtists ->
                if (topArtists.isNotEmpty()) {
                    getSongsByArtists(topArtists.map { it.name }).first().let { artistSongs ->
                        if (artistSongs.isNotEmpty()) {
                            recommendations.add(
                                PlaylistRecommendation(
                                    id = "top_artists_mix",
                                    name = "Your Top Artists Mix",
                                    description = "Featuring ${topArtists.first().name} and more",
                                    imageUrl = artistSongs.firstOrNull()?.coverUrl,
                                    songs = artistSongs.shuffled().take(20)
                                )
                            )
                        }
                    }
                }
            }

            // Get discovery mix
            songRepository.getAllSongs().first().let { allSongs ->
                if (allSongs.isNotEmpty()) {
                    recommendations.add(
                        PlaylistRecommendation(
                            id = "discovery_mix",
                            name = "Discovery Mix",
                            description = "New songs we think you'll like",
                            imageUrl = allSongs.random().coverUrl,
                            songs = allSongs.shuffled().take(20)
                        )
                    )
                }
            }

            emit(recommendations)
        } catch (e: Exception) {
            Log.e("RecommendationRepository", "Error generating recommendations", e)
            emit(emptyList())
        }
    }
}