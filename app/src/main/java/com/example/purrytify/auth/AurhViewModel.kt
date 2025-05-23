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
                val loginResponse = apiService.login(LoginRequest(email, password))

                if (loginResponse.isSuccessful && loginResponse.body() != null) {
                    val token = loginResponse.body()!!.accessToken
                    tokenManager.saveAccessToken(token)
                    loginResponse.body()!!.refreshToken?.let {
                        tokenManager.saveRefreshToken(it)
                    }

                    // Fetch user profile
                    val profileResponse = apiService.getProfile("Bearer $token")
                    if (profileResponse.isSuccessful && profileResponse.body() != null) {
                        val user = profileResponse.body()!!
                        tokenManager.saveUserInfo(user.id, user.location)
                        _loginState.value = ApiResponse.Success(Unit)
                    } else {
                        _loginState.value = ApiResponse.Error("Failed to fetch user profile")
                    }
                } else {
                    _loginState.value = ApiResponse.Error("Login failed: ${loginResponse.message()}")
                }
            } catch (e: Exception) {
                _loginState.value = ApiResponse.Error("Network error: ${e.message}")
            }
        }
    }

    fun logout() {
        tokenManager.clearAll()
    }
}