package com.example.purrytify.ui.charts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.repository.OnlineSongRepository
import com.example.purrytify.model.DownloadStatus
import com.example.purrytify.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import android.util.Log
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.utils.CountryUtils
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableLiveData<DownloadStatus>()
    val downloadStatus: LiveData<DownloadStatus> = _downloadStatus

    fun playOnlineSong(song: Song) {
        musicPlayerManager.playSong(song)
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            try {
                _downloadProgress.value = mapOf(song.id to 0)
                songRepository.downloadSong(song)
                    .collect { progress ->
                        _downloadProgress.value = mapOf(song.id to progress)
                        if (progress == 100) {
                            _downloadStatus.value = DownloadStatus.Success
                        }
                    }
            } catch (e: Exception) {
                _downloadStatus.value = DownloadStatus.Error(e.message ?: "Download failed")
                _downloadProgress.value = mapOf(song.id to 0)
            }
        }
    }

//    fun loadCharts(type: String) {
//        viewModelScope.launch {
//            _isLoading.value = true
//            try {
//                when (type) {
//                    "global" -> {
//                        songRepository.getGlobalTopSongs()
//                            .catch { e ->
//                                _error.value = e.message ?: "Failed to load global songs"
//                                _songs.value = emptyList()
//                            }
//                            .collect { songs ->
//                                _songs.value = songs
//                            }
//                    }
//                    else -> {
//                        songRepository.getCountryTopSongs()
//                            .collect { songs ->
//                                _songs.value = songs
//                            }
//                    }
//                }
//            } catch (e: Exception) {
//                _error.value = e.message ?: "Failed to load songs"
//                _songs.value = emptyList()
//            } finally {
//                _isLoading.value = false
//            }
//        }
//    }

    fun loadCharts(type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (type.uppercase()) {
                    "GLOBAL" -> {
                        songRepository.getGlobalTopSongs()
                            .catch { e ->
                                _error.value = e.message ?: "Failed to load global songs"
                                _songs.value = emptyList()
                            }
                            .collect { songs ->
                                _songs.value = songs
                            }
                    }
                    else -> {
                        // Validate country code
//                        if (!CountryUtils.isCountrySupported(type)) {
//                            throw IllegalArgumentException("Unsupported country code: $type")
//                        }

                        songRepository.getCountryTopSongs()
                            .catch { e ->
                                _error.value = e.message ?: "Failed to load country songs"
                                _songs.value = emptyList()
                            }
                            .collect { songs ->
                                _songs.value = songs
                            }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load songs"
                _songs.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}