package com.example.purrytify.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.auth.AuthRepository
import com.example.purrytify.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userEmail = userPreferencesRepository.userEmail

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            userPreferencesRepository.clearUserData()
        }
    }
}