package com.example.purrytify.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {
    private val _currentTab = MutableLiveData(0)
    val currentTab: LiveData<Int> = _currentTab

    val songs = _currentTab.switchMap { tab ->
        when (tab) {
            0 -> songRepository.getAllSongs().asLiveData()
            1 -> songRepository.getLikedSongs().asLiveData()
            else -> songRepository.getAllSongs().asLiveData()
        }
    }

    fun setTab(position: Int) {
        _currentTab.value = position
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            songRepository.updateSong(song.copy(isLiked = !song.isLiked))
        }
    }
}