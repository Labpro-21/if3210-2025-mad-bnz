package com.example.purrytify.player
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val songDuration = musicPlayer.songDuration

    fun playSong(song: Song) {
        _currentSong.value = song
        musicPlayer.playSong(song)

        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
        }
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }

    fun seekTo(position: Int) {
        musicPlayer.seekTo(position)
    }

    // Update the toggleLike function to accept a Song parameter
    fun toggleLike(song: Song) {
        viewModelScope.launch {
            songRepository.updateSong(song.copy(isLiked = !song.isLiked))
        }
    }

    // Add a parameterless version for toggling the current song
    fun toggleLike() {
        viewModelScope.launch {
            currentSong.value?.let { song ->
                songRepository.updateSong(song.copy(isLiked = !song.isLiked))
                // Update the current song
                _currentSong.value = song.copy(isLiked = !song.isLiked)
            }
        }
    }
}