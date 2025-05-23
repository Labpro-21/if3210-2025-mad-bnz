package com.example.purrytify.repository



import android.util.Log
import com.example.purrytify.model.DailyListening
import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.model.SoundCapsuleEntity
import com.example.purrytify.model.analytics.SongStreakStats
import com.example.purrytify.model.analytics.StreakInfo
import com.example.purrytify.model.analytics.TopArtistStats
import com.example.purrytify.model.analytics.TopSongStats
import com.example.purrytify.network.ApiService
import com.example.purrytify.room.AnalyticsDao
import com.example.purrytify.room.PlayHistoryDao
import com.example.purrytify.room.SoundCapsuleDao
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCapsuleRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val analyticsDao: AnalyticsDao
) {
    fun getMonthlyCapsule(userId: String, month: Int, year: Int): Flow<MonthlyStats> = flow {
        try {
            val yearMonth = String.format("%d-%02d", year, month)
            val startDate = String.format("%d-%02d-01", year, month)
            val endDate = String.format("%d-%02d-%02d", year, month, getLastDayOfMonth(month, year))
            
            // Get daily listening data
            val dailyListening = playHistoryDao.getDailyPlayHistory(
                userId = userId, 
                startDate = startDate,
                endDate = endDate
            )

            // Get top songs
            val topSongs = playHistoryDao.getTopSongs(
                userId = userId,
                startTime = convertToTimestamp(startDate),
                endTime = convertToTimestamp(endDate),
                limit = 10
            )

            // Get top artists 
            val topArtists = playHistoryDao.getTopArtists(
                userId = userId,
                startTime = convertToTimestamp(startDate),
                endTime = convertToTimestamp(endDate),
                limit = 10
            )

            // Get streak stats
            val streakStats = analyticsDao.getSongStreaks(userId)
                .first()
                .maxByOrNull { it.daysCount }

            val totalMinutes = dailyListening.sumOf { it.minutes }
            val dailyAverage = if (dailyListening.isNotEmpty()) {
                totalMinutes / dailyListening.size
            } else 0

            val monthlyStats = MonthlyStats(
                monthYear = "${getMonthName(month)} $year",
                totalMinutes = totalMinutes.toLong(),
                dailyAverage = dailyAverage,
                dailyData = dailyListening,
                topArtists = topArtists,
                topSongs = topSongs,
                currentStreak = streakStats,
                streakInfo = null
            )

            emit(monthlyStats)
        } catch (e: Exception) {
            Log.e("SoundCapsuleRepo", "Error getting monthly capsule", e)
            throw e
        }
    }

    private fun getMonthName(month: Int): String {
        return DateFormatSymbols().months[month - 1]
    }

    private fun getLastDayOfMonth(month: Int, year: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun convertToTimestamp(dateStr: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.time ?: 0L
    }

    suspend fun logPlayback(songId: String, duration: Long, userId: String) {
        try {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(System.currentTimeMillis())
            
            playHistoryDao.insertPlayHistory(
                PlayHistoryEntity(
                    songId = songId,
                    userId = userId,
                    playedAt = System.currentTimeMillis(),
                    duration = duration,
                    date = currentDate
                )
            )
        } catch (e: Exception) {
            Log.e("SoundCapsuleRepo", "Error logging playback", e)
            throw e
        }
    }
}