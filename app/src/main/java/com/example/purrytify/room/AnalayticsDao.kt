package com.example.purrytify.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.purrytify.model.PlayHistoryEntity
import com.example.purrytify.model.Song
import com.example.purrytify.model.analytics.SongStreakStats
import com.example.purrytify.model.analytics.TopArtistStats
import com.example.purrytify.model.analytics.TopSongStats
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(playHistory: PlayHistoryEntity)

    @Query("""
        SELECT SUM(duration) FROM play_history 
        WHERE userId = :userId 
        AND strftime('%Y-%m', datetime(playedAt/1000, 'unixepoch')) = :yearMonth
    """)
    fun getMonthlyListeningTime(userId: String, yearMonth: String): Flow<Long>



    @Query("""
        SELECT title, artist, COUNT(*) as playCount, MIN(rank) as rank 
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE userId = :userId 
        AND strftime('%Y-%m', datetime(ph.playedAt/1000, 'unixepoch')) = :yearMonth
        GROUP BY s.id
        ORDER BY playCount DESC
        LIMIT 10
    """)
    fun getTopSongs(userId: String, yearMonth: String): Flow<List<TopSongStats>>

    @Query("""
        SELECT s.id as songId, s.title as songTitle, s.artist, s.coverUrl as image,
               COUNT(*) as daysCount,
               MIN(ph.playedAt) as startDate,
               MAX(ph.playedAt) as endDate
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE userId = :userId
        GROUP BY songId
        HAVING daysCount >= 2
        ORDER BY daysCount DESC
    """)
    fun getSongStreaks(userId: String): Flow<List<SongStreakStats>>

//    @Query("""
//        SELECT artist as name, COUNT(*) as playCount, MIN(image_url) as imageUrl
//        FROM play_history
//        WHERE user_id = :userId
//        AND strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) = :yearMonth
//        GROUP BY artist
//        ORDER BY playCount DESC
//        LIMIT 10
//    """)
//    suspend fun getTopArtists(userId: String, yearMonth: String): List<TopArtistStats>

    @Query("""
        SELECT artist as name, COUNT(*) as playCount, MIN(coverUrl) as imageUrl 
        FROM play_history ph
        JOIN songs s ON ph.songId = s.id
        WHERE userId = :userId 
        AND strftime('%Y-%m', datetime(ph.playedAt/1000, 'unixepoch')) = :yearMonth
        GROUP BY artist
        ORDER BY playCount DESC
        LIMIT 10
    """)
    fun getTopArtists(userId: String, yearMonth: String): Flow<List<TopArtistStats>>

    @Query("""
        SELECT DISTINCT s.*
        FROM songs s
        WHERE s.artist IN (:artists)
    """)
    suspend fun getSongsByArtists(artists: List<String>): List<Song>
}