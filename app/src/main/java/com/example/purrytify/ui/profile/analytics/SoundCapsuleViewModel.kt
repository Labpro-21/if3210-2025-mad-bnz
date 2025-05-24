package com.example.purrytify.ui.profile.analytics

import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.DailyListening
import com.example.purrytify.model.analytics.SongStreakStats
import com.example.purrytify.model.analytics.SoundCapsule
import com.example.purrytify.model.analytics.StreakInfo
import com.example.purrytify.model.analytics.TopArtistStats
import com.example.purrytify.model.analytics.TopSongStats
import com.example.purrytify.repository.AnalyticsRepository
import com.example.purrytify.repository.SoundCapsuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class SoundCapsuleViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val tokenManager: TokenManager,
    private val soundCapsuleRepository: SoundCapsuleRepository,
) : ViewModel() {

    private val _monthlyStats = MutableLiveData<MonthlyStats>()
    val monthlyStats: LiveData<MonthlyStats> = _monthlyStats


    private val _timeListened = MutableLiveData<TimeListeningStats>()
    val timeListened: LiveData<TimeListeningStats> = _timeListened



    init {
        loadMonthlyStats()
    }

//    fun exportToCSV() {
//        viewModelScope.launch {
//            _exportState.value = ExportState.Loading
//            try {
//                val userId = tokenManager.getUserId() ?: throw Exception("User not logged in")
//                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
//                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
//                val yearMonth = "$currentYear-${String.format("%02d", currentMonth)}"
//
//                val uri = analyticsRepository.exportAnalytics(userId, yearMonth)
//                _exportState.value = ExportState.Success(uri)
//            } catch (e: Exception) {
//                _exportState.value = ExportState.Error(e.message ?: "Export failed")
//            }
//        }
//    }

    fun loadMonthlyStats() {
        viewModelScope.launch {
            try {
                Log.d("SoundCapsuleViewModel", "Loading time listened data...")
                val userId = tokenManager.getUserId() ?: return@launch
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                soundCapsuleRepository.getMonthlyCapsule(userId, currentMonth, currentYear)
                    .collect { stats ->
                        Log.d("SoundCapsuleViewModel", "Received stats: $stats")
                        _timeListened.value = TimeListeningStats(
                            totalMinutes = stats.totalMinutes.toInt(),
                            dailyAverage = (stats.dailyAverage), // or use actual days in month
                            dailyData = stats.dailyData
                        )
                        _monthlyStats.value = stats
                    }
            } catch (e: Exception) {
                Log.e("SoundCapsuleViewModel", "Error loading time listened", e)
            }
        }
    }

    private fun getCurrentYearMonth(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}"
    }

    private fun getDaysInMonth(): Int {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun calculateDailyData(stats: SoundCapsule): List<DailyListening> {
        // Implement daily data calculation logic
        return emptyList() // Placeholder
    }
}
@Parcelize
data class MonthlyStats(
    val monthYear: String,
    val totalMinutes: Long,
    val dailyAverage: Int,
    val topArtists: List<TopArtistStats>,
    val topSongs: List<TopSongStats>,
    val streakInfo: StreakInfo?,
    val dailyData: List<DailyListening>,
    val currentStreak: SongStreakStats? = null
) : Parcelable

@Parcelize
data class TimeListeningStats(
    val totalMinutes: Int,
    val dailyAverage: Int,
    val dailyData: List<DailyListening>
) : Parcelable

