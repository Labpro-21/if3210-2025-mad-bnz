package com.example.purrytify.repository

import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.network.ApiService
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun getProfile(): ApiResponse<User> {
        return try {
            val token = tokenManager.getAccessToken()
            if (token == null) {
                ApiResponse.Error("Not authenticated")
            } else {
                val response = apiService.getProfile("Bearer $token")
                if (response.isSuccessful) {
                    ApiResponse.Success(response.body()!!)
                } else {
                    ApiResponse.Error(response.message())
                }
            }
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error")
        }
    }
}