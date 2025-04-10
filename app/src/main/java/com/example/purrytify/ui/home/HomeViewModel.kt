package com.example.purrytify.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    val recentlyPlayed: LiveData<List<Song>> = songRepository.getRecentlyPlayed().asLiveData()
    val newSongs: LiveData<List<Song>> = songRepository.getNewSongs().asLiveData()

    fun playSong(song: Song) {
        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
            // You might need to communicate with PlayerService/MusicPlayer here
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            // Create a copy of the song with toggled isLiked value
            val updatedSong = song.copy(isLiked = !song.isLiked)

            // Update the song in the repository
            songRepository.updateSong(updatedSong)
        }
    }
}