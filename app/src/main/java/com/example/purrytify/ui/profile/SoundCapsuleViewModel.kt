package com.example.purrytify.ui.profile

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.purrytify.model.SoundCapsule
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.utils.DateFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SoundCapsuleViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val dateFormatter: DateFormatter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _soundCapsule = MutableLiveData<SoundCapsule?>()
    val soundCapsule: LiveData<SoundCapsule?> = _soundCapsule

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            try {
                val playedSongs = songRepository.getPlayedSongs().first()
                val timeListened = calculateTimeListened(playedSongs)
                val topArtists = calculateTopArtists(playedSongs)
                val topSongs = calculateTopSongs(playedSongs)
                val streaks = calculateStreaks(playedSongs)

                _soundCapsule.value = SoundCapsule(
                    month = getCurrentMonth(),
                    year = getCurrentYear(),
                    timeListened = timeListened,
                    topArtists = topArtists,
                    topSongs = topSongs,
                    streaks = streaks
                )
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error loading analytics", e)
                _soundCapsule.value = null
            }
        }
    }

    fun exportAnalytics() {
        viewModelScope.launch {
            try {
                _soundCapsule.value?.let { capsule ->
                    val csv = generateCsvContent(capsule)
                    saveCsvFile(csv)
                }
            } catch (e: Exception) {
                Log.e("SoundCapsuleVM", "Error exporting analytics", e)
            }
        }
    }

    private fun generateCsvContent(capsule: SoundCapsule): String {
        return buildString {
            appendLine("Sound Capsule - ${capsule.month} ${capsule.year}")
            appendLine()
            appendLine("Time Listened: ${capsule.timeListened} minutes")
            appendLine()
            appendLine("Top Artists:")
            capsule.topArtists.forEach { artist ->
                appendLine("${artist.rank},${artist.name},${artist.playCount} plays")
            }
            appendLine()
            appendLine("Top Songs:")
            capsule.topSongs.forEach { song ->
                appendLine("${song.rank},${song.title},${song.artist},${song.playCount} plays")
            }
            appendLine()
            appendLine("Streaks:")
            capsule.streaks.forEach { streak ->
                appendLine("${streak.songTitle},${streak.artist},${streak.daysCount} days,${dateFormatter.formatDate(streak.startDate)} - ${dateFormatter.formatDate(streak.endDate)}")
            }
        }
    }
}