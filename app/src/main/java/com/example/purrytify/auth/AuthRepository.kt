package com.example.purrytify.auth

import com.example.purrytify.network.ApiService
import com.example.purrytify.auth.TokenManager
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun logout() {
        // Add any API logout call if needed
        tokenManager.clearTokens()
    }
}