package com.example.purrytify.auth
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.network.ApiService
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.network.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _loginState = MutableLiveData<ApiResponse<Unit>>()
    val loginState: LiveData<ApiResponse<Unit>> = _loginState

    fun isLoggedIn(): Boolean {
        return tokenManager.getAccessToken() != null
    }

    fun login(email: String, password: String) {
        _loginState.value = ApiResponse.Loading

        viewModelScope.launch {
            try {
                val response = apiService.login(LoginRequest(email, password))
                if (response.isSuccessful) {
                    val loginResponse = response.body()

                    // Add null check here:
                    if (loginResponse?.accessToken != null) {
                        tokenManager.saveAccessToken(loginResponse.accessToken)
                        if (loginResponse.refreshToken != null) {
                            tokenManager.saveRefreshToken(loginResponse.refreshToken)
                        }
                        _loginState.value = ApiResponse.Success(Unit)
                    } else {
                        _loginState.value = ApiResponse.Error("Server returned empty token")
                    }
                } else {
                    _loginState.value = ApiResponse.Error("Login failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _loginState.value = ApiResponse.Error("Network error: ${e.message}")
            }
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}