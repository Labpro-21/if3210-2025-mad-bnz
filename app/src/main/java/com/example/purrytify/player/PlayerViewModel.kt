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
    private val musicPlayerManager: MusicPlayerManager,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _playlist = MutableLiveData<List<Song>>(emptyList())
    val playlist: LiveData<List<Song>> = _playlist

    val isPlaying = musicPlayer.isPlaying
    val currentPosition = musicPlayer.currentPosition
    val songDuration = musicPlayer.songDuration

    private val _likedSongs = MutableLiveData<List<Song>>(emptyList())
    val likedSongs: LiveData<List<Song>> = _likedSongs

    init {
        viewModelScope.launch {
            songRepository.getLikedSongs().collectLatest { songs ->
                _likedSongs.postValue(songs)
            }
        }
    }

    fun preload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicPlayerManager.initialize()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        musicPlayer.playSong(song)

        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())


            if (_playlist.value.isNullOrEmpty()) {

                val songs = songRepository.getAllSongs().firstOrNull() ?: emptyList()
                _playlist.postValue(songs)
            }
        }
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }

    fun seekTo(position: Int) {
        musicPlayer.seekTo(position)
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


    fun playNextSong() {
        val currentPlaylist = _playlist.value ?: return
        val currentSongIndex = currentPlaylist.indexOfFirst { it.id == currentSong.value?.id }

        if (currentSongIndex != -1 && currentSongIndex < currentPlaylist.size - 1) {
            val nextSong = currentPlaylist[currentSongIndex + 1]
            playSong(nextSong)
        } else if (currentPlaylist.isNotEmpty()) {

            playSong(currentPlaylist[0])
        }
    }

    fun playPreviousSong() {
        val currentPlaylist = _playlist.value ?: return
        val currentSongIndex = currentPlaylist.indexOfFirst { it.id == currentSong.value?.id }

        if (currentSongIndex > 0) {
            val previousSong = currentPlaylist[currentSongIndex - 1]
            playSong(previousSong)
        } else if (currentPlaylist.isNotEmpty()) {
            // Loop to the last song if we're at the beginning
            playSong(currentPlaylist.last())
        }
    }

    fun getCurrentPosition(): Int {
        return musicPlayer.getCurrentPosition()
    }

    override fun onCleared() {
        super.onCleared()

        musicPlayer.stop()
    }
}