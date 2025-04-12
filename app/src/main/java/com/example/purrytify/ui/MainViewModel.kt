package com.example.purrytify.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.auth.AuthRepository
import com.example.purrytify.auth.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            authRepository.logout()
        }
    }
}