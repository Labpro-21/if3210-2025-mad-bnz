package com.example.purrytify.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _profile = MutableStateFlow<ApiResponse<User>>(ApiResponse.Loading)
    val profile: StateFlow<ApiResponse<User>> = _profile

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = ApiResponse.Loading
            _profile.value = userRepository.getProfile()
        }
    }
}