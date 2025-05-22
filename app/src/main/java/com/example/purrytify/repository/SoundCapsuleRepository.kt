package com.example.purrytify.repository

import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.model.SoundCapsuleEntity
import com.example.purrytify.room.PlayHistoryDao
import com.example.purrytify.room.SoundCapsuleDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCapsuleRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val soundCapsuleDao: SoundCapsuleDao,
    private val songRepository: SongRepository
) {
    suspend fun logPlayback(songId: String, duration: Long, userId: String) {
        playHistoryDao.insertPlayHistory(
            PlayHistoryEntity(
                songId = songId,
                timestamp = System.currentTimeMillis(),
                duration = duration,
                userId = userId
            )
        )
    }

    suspend fun generateMonthlyCapsule(userId: String, month: Int, year: Int) {
        val startTime = getMonthStartTime(month, year)
        val endTime = getMonthEndTime(month, year)

        val timeListened = playHistoryDao.getTotalTimeListened(userId, startTime, endTime)
        val topSongs = playHistoryDao.getTopSongs(userId, startTime, endTime)
        val streaks = calculateStreaks(playHistoryDao.getDailyPlayHistory(userId))

        // Create and save capsule
        val capsule = SoundCapsuleEntity(
            userId = userId,
            month = month,
            year = year,
            totalTimeListened = timeListened / (1000 * 60), // convert to minutes
            dailyAverage = ((timeListened / (1000 * 60)) / getDaysInMonth(month, year)).toInt()
        )

        soundCapsuleDao.insertCapsule(capsule)
        // Insert other related entities...
    }
}