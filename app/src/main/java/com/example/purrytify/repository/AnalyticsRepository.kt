package com.example.purrytify.repository

import android.net.Uri
import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.room.AnalyticsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val analyticsDao: AnalyticsDao,
    private val fileExporter: FileExporter
) {
    suspend fun logPlayback(songId: String, duration: Long, userId: String) {
        analyticsDao.insertPlayHistory(
            PlayHistoryEntity(
                songId = songId,
                userId = userId,
                duration = duration
            )
        )
    }

    fun getMonthlyStats(userId: String, yearMonth: String): Flow<SoundCapsule> = flow {
        combine(
            analyticsDao.getMonthlyListeningTime(userId, yearMonth),
            analyticsDao.getTopArtists(userId, yearMonth),
            analyticsDao.getTopSongs(userId, yearMonth),
            analyticsDao.getSongStreaks(userId)
        ) { timeListened, topArtists, topSongs, streaks ->
            SoundCapsule(
                timeListened = timeListened / (1000 * 60), // Convert to minutes
                topArtists = topArtists,
                topSongs = topSongs,
                streaks = streaks
            )
        }.collect { capsule ->
            emit(capsule)
        }
    }

    suspend fun exportAnalytics(userId: String, yearMonth: String): Uri {
        val capsule = getMonthlyStats(userId, yearMonth).first()
        return fileExporter.exportToCsv(capsule, "analytics_$yearMonth.csv")
    }
}