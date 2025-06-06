package com.example.purrytify.ui.profile

import android.app.Application
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
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.repository.SoundCapsuleRepository
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import com.example.purrytify.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import androidx.core.net.toFile
import androidx.lifecycle.AndroidViewModel
import com.example.purrytify.repository.AnalyticsRepository
import dagger.hilt.android.internal.Contexts.getApplication
import okhttp3.MultipartBody




@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val songRepository: SongRepository,
    private val authRepository: AuthRepository,
    private val apiService: ApiService,
    private val musicPlayerManager: MusicPlayerManager,
    private val tokenManager: TokenManager,
    private val soundCapsuleRepository: SoundCapsuleRepository,
    private val analyticsRepository: AnalyticsRepository,
    application: Application
) : AndroidViewModel(application) {

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

    val currentSong = musicPlayerManager.currentSong
    val isPlaying = musicPlayerManager.isPlaying
    val currentPosition = musicPlayerManager.currentPosition

    private val _soundCapsule = MutableLiveData<MonthlyStats>()
    val soundCapsule: LiveData<MonthlyStats> = _soundCapsule

    private val _updateProfileState = MutableLiveData<ApiResponse<Unit>>()
    val updateProfileState: LiveData<ApiResponse<Unit>> = _updateProfileState

    private val _userData = MutableLiveData<User?>(null)
    val userData: LiveData<User?> = _userData

    private val _exportState = MutableLiveData<ExportState>()
    val exportState: LiveData<ExportState> = _exportState

    init {
        loadProfile()
        loadSoundCapsule()
    }

    private fun loadSoundCapsule() {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId() ?: return@launch
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                soundCapsuleRepository.getMonthlyCapsule(userId, currentMonth, currentYear)
                    .collectLatest { capsule ->
                        _soundCapsule.value = capsule
                    }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading sound capsule", e)
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun updateLocation(location: String) {
        viewModelScope.launch {
            try {
                _updateProfileState.value = ApiResponse.Loading
                updateProfile(location = location, imageUri = null)
            } catch (e: Exception) {
                _updateProfileState.value = ApiResponse.Error(e.message ?: "Failed to update location")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
        }
    }


    fun getCurrentUserCountry(): String {
        return tokenManager.getUserCountry() ?: throw IllegalStateException("User not logged in")
    }

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

    fun updateProfile(location: String?, imageUri: Uri?) {
        viewModelScope.launch {
            _updateProfileState.value = ApiResponse.Loading
            try {
                val token = tokenManager.getAccessToken() ?: throw Exception("No token found")
                val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

                location?.let {
                    multipartBuilder.addFormDataPart("location", it)
                }

                imageUri?.let { uri ->
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                        val tempFile = File.createTempFile(
                            "profile_",
                            ".jpg",
                            getApplication<Application>().cacheDir
                        ).apply {
                            outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                        multipartBuilder.addFormDataPart("profilePhoto", "profile_photo.jpg", requestBody)
                    }
                }

                val response = apiService.updateProfile(
                    "Bearer $token",
                    multipartBuilder.build()
                )

                if (response.isSuccessful) {
                    _updateProfileState.value = ApiResponse.Success(Unit)
                    loadProfile()
                } else {
                    _updateProfileState.value = ApiResponse.Error("Update failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _updateProfileState.value = ApiResponse.Error(e.message ?: "Update failed")
                Log.e("ProfileViewModel", "Error updating profile", e)
            }
        }
    }


    fun createTempFileFromUri(uri: Uri): File {
        val tempFile = File.createTempFile("profile_", ".jpg").apply {
            deleteOnExit()
        }
        uri.toFile().inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun exportToCSV() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val userId = tokenManager.getUserId() ?: throw Exception("User not logged in")
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val yearMonth = "$currentYear-${String.format("%02d", currentMonth)}"

                val uri = analyticsRepository.exportAnalytics(userId, yearMonth)
                _exportState.value = ExportState.Success(uri)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }




}

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val uri: Uri) : ExportState()
    data class Error(val message: String) : ExportState()
}