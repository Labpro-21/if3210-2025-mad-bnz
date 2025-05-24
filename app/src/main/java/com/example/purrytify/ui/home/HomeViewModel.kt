package com.example.purrytify.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.PlaylistRecommendation
import com.example.purrytify.model.Song
import com.example.purrytify.model.User
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.repository.OnlineSongRepository
import com.example.purrytify.repository.RecommendationRepository
import com.example.purrytify.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val userRepository: UserRepository,
    private val onlineSongRepository: OnlineSongRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val tokenManager: TokenManager,
    private val recommendationRepository: RecommendationRepository,
) : ViewModel() {

    private val _userInfo = MutableLiveData<User?>()
    val userInfo: LiveData<User?> = _userInfo

    private val _recentlyPlayedSongs = MutableLiveData<List<Song>>()
    val recentlyPlayedSongs: LiveData<List<Song>> = _recentlyPlayedSongs

    private val _newReleaseSongs = MutableLiveData<List<Song>>()
    val newReleaseSongs: LiveData<List<Song>> = _newReleaseSongs

    private val _recommendedSongs = MutableLiveData<List<Song>>()
    val recommendedSongs: LiveData<List<Song>> = _recommendedSongs

    private val _globalTopSongs = MutableLiveData<List<Song>>()
    private val _countryTopSongs = MutableLiveData<List<Song>>()
    private val _recommendedPlaylists = MutableLiveData<List<PlaylistRecommendation>>()
    val recommendedPlaylists: LiveData<List<PlaylistRecommendation>> = _recommendedPlaylists


    init {
        loadUserInfo()
        loadRecentlyPlayedSongs()
        loadNewReleases()
        loadRecommendations()
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                recommendationRepository.getDailyRecommendations()
                    .collect { playlists ->
                        _recommendedPlaylists.value = playlists
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading recommendations", e)
            }
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val response = userRepository.getProfile()
                if (response is ApiResponse.Success) {
                    _userInfo.value = response.data
                } else if (response is ApiResponse.Error) {
                    Log.e("HomeViewModel", "Error: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading user profile", e)
            }
        }
    }

    private fun loadRecentlyPlayedSongs() {
        viewModelScope.launch {
            try {

                songRepository.getAllSongs().collect { songs ->
                    _recentlyPlayedSongs.value = songs.sortedByDescending { it.lastPlayed }
                        .take(40)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading recent songs", e)
            }
        }
    }

    private fun loadNewReleases() {
        viewModelScope.launch {
            try {

                songRepository.getRecentlyAddedSongs().collect { songs ->
                    _newReleaseSongs.value = songs.sortedByDescending { it.id }

                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading new releases", e)
            }
        }
    }


    fun playSong(song: Song) {
        musicPlayerManager.playSong(song)
        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
        }
    }

    fun loadTopSongs() {
        viewModelScope.launch {
            onlineSongRepository.getGlobalTopSongs()
                .collect { songs ->
                    _globalTopSongs.value = songs
                }
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            songRepository.updateSong(song.copy(isLiked = !song.isLiked))
        }
    }
    fun getCurrentUserCountry(): String {
        return tokenManager.getUserCountry() ?: throw IllegalStateException("User not logged in")
    }
}