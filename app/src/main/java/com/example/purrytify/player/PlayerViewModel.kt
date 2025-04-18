package com.example.purrytify.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicPlayer: MusicPlayer,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong



    val isPlaying = musicPlayer.isPlaying
    val currentPosition = musicPlayer.currentPosition

    private val _likedSongs = MutableLiveData<List<Song>>(emptyList())
    val likedSongs: LiveData<List<Song>> = _likedSongs

    init {
        viewModelScope.launch {
            songRepository.getLikedSongs().collectLatest { songs ->
                _likedSongs.postValue(songs)
            }
        }
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val updatedSong = song.copy(isLiked = !song.isLiked)
            songRepository.updateSong(updatedSong)
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            currentSong.value?.let { song ->
                songRepository.updateSong(song.copy(isLiked = !song.isLiked))
                _currentSong.value = song.copy(isLiked = !song.isLiked)
            }
        }
    }
    fun isLiked(songId: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>(false)

        viewModelScope.launch {
            val isLiked = songRepository.getSongById(songId)?.isLiked ?: false
            result.postValue(isLiked)
        }

        return result
    }


    fun getCurrentPosition(): Int {
        return musicPlayer.getCurrentPosition()
    }

    override fun onCleared() {
        super.onCleared()

        musicPlayer.stop()
    }
}