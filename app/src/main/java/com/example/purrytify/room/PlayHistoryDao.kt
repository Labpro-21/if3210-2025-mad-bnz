package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.purrytify.model.DailyListening
import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.model.analytics.TopArtistStats
import com.example.purrytify.model.analytics.TopSongStats
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insertPlayHistory(playHistory: PlayHistoryEntity)

    @Query("""
        SELECT SUM(duration) 
        FROM play_history 
        WHERE userId = :userId 
        AND date BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalTimeListened(userId: String, startTime: Long, endTime: Long): Long

    @Query("""
        SELECT 
            s.id as songId,
            s.title as title,
            s.artist as artist,
            COUNT(*) as playCount,
            s.coverUrl as imageUrl
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE ph.userId = :userId 
        AND ph.playedAt BETWEEN :startTime AND :endTime 
        GROUP BY s.id, s.title, s.artist
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    suspend fun getTopSongs(userId: String, startTime: Long, endTime: Long, limit: Int = 10): List<TopSongStats>


    @Query("""
        SELECT date as playDate, SUM(duration)/60000 as minutes
        FROM play_history 
        WHERE userId = :userId 
        AND date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyPlayHistory(userId: String, startDate: String, endDate: String): List<DailyListening>

    @Query("""
        SELECT s.artist as name, COUNT(*) as playCount, s.coverUrl as imageUrl
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE ph.userId = :userId
        AND ph.playedAt BETWEEN :startTime AND :endTime
        GROUP BY s.artist
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(userId: String, startTime: Long, endTime: Long, limit: Int): List<TopArtistStats>

    @Query("""
        SELECT * FROM play_history 
        WHERE userId = :userId
        ORDER BY playedAt DESC
    """)
    fun getPlayHistory(userId: String): Flow<List<PlayHistoryEntity>>

    @Query("""
        SELECT SUM(duration) FROM play_history 
        WHERE userId = :userId 
        AND strftime('%Y-%m', datetime(playedAt/1000, 'unixepoch')) = :yearMonth
    """)
    fun getMonthlyListeningTime(userId: String, yearMonth: String): Flow<Long>
}