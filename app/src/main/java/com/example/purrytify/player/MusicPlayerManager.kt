package com.example.purrytify.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
//    private val context: Context
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false
    private var positionUpdateJob: Job? = null

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Int>(0)
    val currentPosition: LiveData<Int> = _currentPosition

    private val _songDuration = MutableLiveData<Int>(0)
    val songDuration: LiveData<Int> = _songDuration

    private val _playlist = mutableListOf<Song>()
    private var currentIndex = -1

    private val _currentPlaylist = MutableLiveData<List<Song>>(emptyList())
    val currentPlaylist: LiveData<List<Song>> = _currentPlaylist

    fun initialize() {
        if (!isInitialized) {
            isInitialized = true
            Log.d("MusicPlayerManager", "Initialized")
        }
    }

    fun playSong(song: Song) {
        _currentSong.postValue(song)

        val songIndex = _playlist.indexOfFirst { it.id == song.id }
        currentIndex = if (songIndex != -1) songIndex else {
            _playlist.add(song)
            _playlist.size - 1
        }
        _currentPlaylist.postValue(_playlist.toList())
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.path))
                prepareAsync()

                setOnPreparedListener {
                    start()
                    _isPlaying.postValue(true)
                    _songDuration.postValue(duration)
                    startPositionUpdates()
                    Log.d("MusicPlayerManager", "Now playing: ${song.title} - ${song.artist}")
                }

                setOnCompletionListener {
                    _isPlaying.postValue(false)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("MusicPlayerManager", "MediaPlayer error: $what, $extra")
                    _isPlaying.postValue(false)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error playing song", e)
            _isPlaying.postValue(false)
        }
    }
    fun setPlaylist(songs: List<Song>, startSongId: String? = null) {
        _playlist.clear()
        _playlist.addAll(songs)
        _currentPlaylist.postValue(_playlist.toList())
        currentIndex = if (startSongId != null) {
            val index = _playlist.indexOfFirst { it.id == startSongId }
            if (index != -1) index else 0
        } else {
            0
        }
        Log.d("MusicPlayerManager", "Playlist updated with ${songs.size} songs, starting at index $currentIndex")
    }
    fun playNextSong() {
        viewModelScope.launch {
            val currentSongId = _currentSong.value?.id ?: return@launch

            songRepository.getAllSongs().first()?.let { songs ->
                val currentIndex = songs.indexOfFirst { it.id == currentSongId }
                if (currentIndex == -1) return@launch
                val nextSong = if (currentIndex < songs.size - 1) {
                    songs[currentIndex + 1]
                } else {
                    songs.firstOrNull()
                }
                nextSong?.let {
                    playSong(it)
                }
            }
        }
    }

    fun playPreviousSong() {
        viewModelScope.launch {
            val currentSongId = _currentSong.value?.id ?: return@launch
            if (getCurrentPosition() > 3000) {
                mediaPlayer?.seekTo(0)
                return@launch
            }
            songRepository.getAllSongs().first()?.let { songs ->
                val currentIndex = songs.indexOfFirst { it.id == currentSongId }
                if (currentIndex == -1) return@launch
                val previousSong = if (currentIndex > 0) {
                    songs[currentIndex - 1]
                } else {
                    songs.lastOrNull()
                }
                previousSong?.let {
                    playSong(it)
                }
            }
        }
    }

    fun getPlaylist(): List<Song> {
        return _playlist.toList()
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.postValue(false)
            } else {
                player.start()
                _isPlaying.postValue(true)
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.postValue(position)
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && mediaPlayer != null) {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _currentPosition.postValue(it.currentPosition)
                        }
                    }
                    delay(1000)
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error updating position", e)
                }
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun releaseMediaPlayer() {
        stopPositionUpdates()
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun release() {
        releaseMediaPlayer()
        isInitialized = false
    }
}