package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Query("""
        SELECT SUM(duration) FROM play_history 
        WHERE strftime('%Y-%m', datetime(playedAt/1000, 'unixepoch')) = :yearMonth
        AND userId = :userId
    """)
    fun getMonthlyListeningTime(userId: String, yearMonth: String): Flow<Long>

    @Query("""
        SELECT s.artist, COUNT(*) as playCount 
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE strftime('%Y-%m', datetime(ph.playedAt/1000, 'unixepoch')) = :yearMonth
        AND ph.userId = :userId
        GROUP BY s.artist
        ORDER BY playCount DESC
        LIMIT 10
    """)
    fun getTopArtists(userId: String, yearMonth: String): Flow<List<TopArtistStats>>

    @Query("""
        SELECT s.*, COUNT(*) as playCount 
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE strftime('%Y-%m', datetime(ph.playedAt/1000, 'unixepoch')) = :yearMonth
        AND ph.userId = :userId
        GROUP BY s.id
        ORDER BY playCount DESC
        LIMIT 10
    """)
    fun getTopSongs(userId: String, yearMonth: String): Flow<List<TopSongStats>>

    @Query("""
        WITH ConsecutiveDays AS (
            SELECT songId,
                   date,
                   ROW_NUMBER() OVER (PARTITION BY songId ORDER BY date) as rn,
                   date(date, '-' || ROW_NUMBER() OVER (PARTITION BY songId ORDER BY date) || ' days') as grp
            FROM (SELECT DISTINCT songId, date FROM play_history WHERE userId = :userId)
        )
        SELECT 
            s.*,
            COUNT(*) as streak_days,
            MIN(date) as streak_start,
            MAX(date) as streak_end
        FROM ConsecutiveDays cd
        JOIN songs s ON cd.songId = s.id
        GROUP BY songId, grp
        HAVING COUNT(*) >= 2
        ORDER BY streak_days DESC, streak_end DESC
    """)
    fun getSongStreaks(userId: String): Flow<List<SongStreakStats>>
}