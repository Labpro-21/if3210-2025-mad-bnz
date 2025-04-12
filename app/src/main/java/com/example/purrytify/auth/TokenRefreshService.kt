package com.example.purrytify.auth

import android.app.Service
import android.app.Service.START_STICKY
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.network.ApiService
import com.example.purrytify.network.RefreshTokenRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TokenRefreshService : LifecycleService() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var apiService: ApiService

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TokenRefreshService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!isRunning) {
            isRunning = true
            startTokenCheck()
        }

        return START_STICKY
    }

    private fun startTokenCheck() {
        serviceScope.launch {
            while (isActive) {
                checkToken()
                delay(4 * 60 * 1000L)
            }
        }
    }

    private suspend fun checkToken() {
        val accessToken = tokenManager.getAccessToken()

        if (accessToken != null) {
            try {
                val response = apiService.verifyToken("Bearer $accessToken")
                if (!response.isSuccessful) {
                    refreshToken()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying token: ${e.message}")
                refreshToken()
            }
        }
    }

    private suspend fun refreshToken() {
        val refreshToken = tokenManager.getRefreshToken()

        if (refreshToken != null) {
            try {
                val refreshResponse = apiService.refreshToken(RefreshTokenRequest(refreshToken))

                if (refreshResponse.isSuccessful) {
                    refreshResponse.body()?.let { loginResponse ->
                        tokenManager.saveAccessToken(loginResponse.accessToken)
                        loginResponse.refreshToken?.let {
                            tokenManager.saveRefreshToken(it)
                        }
                        Log.d(TAG, "Token refreshed successfully")
                    }
                } else {
                    Log.e(TAG, "Failed to refresh token: ${refreshResponse.code()}")
                    tokenManager.clearTokens()
                    sendLogoutBroadcast()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token: ${e.message}")
            }
        } else {
            tokenManager.clearTokens()
            sendLogoutBroadcast()
        }
    }

    private fun sendLogoutBroadcast() {
        val intent = Intent(ACTION_LOGOUT)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isRunning = false
    }

    companion object {
        private const val TAG = "TokenRefreshService"
        const val ACTION_LOGOUT = "com.example.purrytify.LOGOUT"
    }
}