package com.example.purrytify.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {
    val recentlyPlayed: LiveData<List<Song>> = songRepository.getRecentlyPlayed().asLiveData()
    val newSongs: LiveData<List<Song>> = songRepository.getNewSongs().asLiveData()

    fun playSong(song: Song) {
        // Implement play logic
    }
}