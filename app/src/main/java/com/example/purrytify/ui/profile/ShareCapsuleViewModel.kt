package com.example.purrytify.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShareCapsuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _stats = MutableLiveData<MonthlyStats>()
    val stats: LiveData<MonthlyStats> = _stats

    init {
        savedStateHandle.get<MonthlyStats>("monthlyStats")?.let {
            _stats.value = it
        }
    }
}