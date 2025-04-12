package com.example.purrytify.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NetworkStateManager private constructor() {
    private val _connectionState = MutableLiveData<Boolean>()
    val connectionState: LiveData<Boolean> = _connectionState

    fun setConnected(isConnected: Boolean) {
        _connectionState.postValue(isConnected)
    }

    companion object {
        @Volatile
        private var instance: NetworkStateManager? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: NetworkStateManager().also { instance = it }
            }
    }
}