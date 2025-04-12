package com.example.purrytify.ui.profile

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.BuildConfig
import com.example.purrytify.auth.AuthRepository
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.network.ApiService
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val songRepository: SongRepository,
    private val authRepository: AuthRepository,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _profile = MutableStateFlow<ApiResponse<User>>(ApiResponse.Loading)
    val profile: StateFlow<ApiResponse<User>> = _profile

    private val _totalSongs = MutableLiveData(0)
    val totalSongs: LiveData<Int> = _totalSongs

    private val _likedSongs = MutableLiveData(0)
    val likedSongs: LiveData<Int> = _likedSongs

    private val _listenedSongs = MutableLiveData(0)
    val listenedSongs: LiveData<Int> = _listenedSongs

    private val _uploadProgress = MutableLiveData<ApiResponse<String>?>(null)
    val uploadProgress: LiveData<ApiResponse<String>?> = _uploadProgress

    fun loadProfile() {
        _profile.value = ApiResponse.Loading
        viewModelScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val response = apiService.getProfile("Bearer $token")

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    _profile.value = ApiResponse.Success(user)
                    Log.d("ProfileViewModel", "Profile loaded: ${user.username}")
                } else {
                    _profile.value = ApiResponse.Error("Failed to load profile: ${response.message()}")
                    Log.e("ProfileViewModel", "Error loading profile: ${response.code()} ${response.message()}")
                }

                loadSongCounts()

            } catch (e: Exception) {
                _profile.value = ApiResponse.Error("Error: ${e.localizedMessage}")
                Log.e("ProfileViewModel", "Exception loading profile", e)
            }
        }
    }
    private fun loadSongCounts() {
        viewModelScope.launch {
            try {
                val allSongs = songRepository.getAllSongs().first()
                _totalSongs.postValue(allSongs.size)
                val likedSongs = songRepository.getLikedSongs().first()
                _likedSongs.postValue(likedSongs.size)
                val playedSongs = songRepository.getPlayedSongs().first()
                _listenedSongs.postValue(playedSongs.size)

                Log.d("ProfileViewModel", "Song counts loaded: Total=${allSongs.size}, Liked=${likedSongs.size}, Played=${playedSongs.size}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading song counts", e)
            }
        }
    }
    private fun loadListenedSongs() {
        viewModelScope.launch {
            try {
                val listened = songRepository.getPlayedSongs().first().size
                _listenedSongs.postValue(listened)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading listened songs", e)
                _listenedSongs.postValue(0)
            }
        }
    }

    fun getBaseUrl(): String {
        return Constants.BASE_URL
    }
    fun uploadProfilePhoto(imageUri: Uri, context: Context) {
        _uploadProgress.value = ApiResponse.Loading

        viewModelScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val fileInputStream = context.contentResolver.openInputStream(imageUri)
                val file = File(context.cacheDir, "profile_image.jpg")
                fileInputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("profilePhoto", file.name, requestFile)
                val response = apiService.updateProfilePhoto("Bearer $token", imagePart)

                if (response.isSuccessful) {
                    _uploadProgress.postValue(ApiResponse.Success("Profile photo updated"))
                    loadProfile()
                } else {
                    _uploadProgress.postValue(ApiResponse.Error("Upload failed: ${response.message()}"))
                }
            } catch (e: Exception) {
                _uploadProgress.postValue(ApiResponse.Error("Error: ${e.localizedMessage}"))
                Log.e("ProfileViewModel", "Error uploading profile photo", e)
            }
        }
    }
}