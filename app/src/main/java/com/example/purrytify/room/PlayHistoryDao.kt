package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.purrytify.model.PlayHistoryEntity

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insertPlayHistory(playHistory: PlayHistoryEntity)

    @Query("""
        SELECT SUM(duration) 
        FROM play_history 
        WHERE userId = :userId 
        AND timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalTimeListened(userId: String, startTime: Long, endTime: Long): Long

    @Query("""
        SELECT songId, COUNT(*) as playCount 
        FROM play_history 
        WHERE userId = :userId 
        AND timestamp BETWEEN :startTime AND :endTime 
        GROUP BY songId 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    suspend fun getTopSongs(userId: String, startTime: Long, endTime: Long, limit: Int = 10): List<SongPlayCount>

    @Query("""
        SELECT DISTINCT date(timestamp/1000, 'unixepoch') as playDate, songId
        FROM play_history
        WHERE userId = :userId
        ORDER BY playDate ASC
    """)
    suspend fun getDailyPlayHistory(userId: String): List<DailyPlay>
}