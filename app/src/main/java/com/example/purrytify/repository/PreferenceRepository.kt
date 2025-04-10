package com.example.purrytify.repository

import com.example.purrytify.auth.TokenManager
import javax.inject.Inject

class PreferenceRepository @Inject constructor(
    private val tokenManager: TokenManager
) {
    fun getAccessToken(): String? = tokenManager.getAccessToken()
    fun saveAccessToken(token: String) = tokenManager.saveAccessToken(token)
    fun clearTokens() = tokenManager.clearTokens()
}