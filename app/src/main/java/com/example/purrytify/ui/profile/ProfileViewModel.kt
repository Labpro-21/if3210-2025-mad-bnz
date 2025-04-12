package com.example.purrytify.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.BuildConfig
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<ApiResponse<User>>(ApiResponse.Loading)
    val profile: StateFlow<ApiResponse<User>> = _profile

    private val _totalSongs = MutableLiveData(0)
    val totalSongs: LiveData<Int> = _totalSongs

    private val _likedSongs = MutableLiveData(0)
    val likedSongs: LiveData<Int> = _likedSongs

    private val _listenedSongs = MutableLiveData(0)
    val listenedSongs: LiveData<Int> = _listenedSongs

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = ApiResponse.Loading
                // Assuming userRepository.getUserProfile() returns a User directly
                val userResponse = userRepository.getProfile()
                _profile.value = userResponse

                // After loading profile, load song statistics
                loadSongStatistics()
            } catch (e: Exception) {
                _profile.value = ApiResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadSongStatistics() {
        viewModelScope.launch {
            songRepository.getAllSongs().collect { songs ->
                _totalSongs.value = songs.size
                _likedSongs.value = songs.count { it.isLiked }
                _listenedSongs.value = songs.count { it.lastPlayed > 0 }

            }
        }
    }

    fun getBaseUrl(): String {
        return Constants.BASE_URL
    }
}