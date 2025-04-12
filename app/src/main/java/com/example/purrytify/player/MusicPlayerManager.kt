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

    private val _isShuffleEnabled = MutableLiveData<Boolean>(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.OFF)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private val originalPlaylist = mutableListOf<Song>()

    enum class RepeatMode {
        OFF, REPEAT_ALL, REPEAT_ONE
    }


    fun initialize() {
        if (!isInitialized) {
            isInitialized = true
            Log.d("MusicPlayerManager", "Initialized")
        }
    }

    fun playSong(song: Song) {
        val currentlyPlaying = _currentSong.value
        if (currentlyPlaying?.id == song.id && mediaPlayer?.isPlaying == true) {
            _isPlaying.postValue(true)
            return
        }
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
                    playNextSong()
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

    fun toggleShuffle() {
        val newValue = !(_isShuffleEnabled.value ?: false)
        _isShuffleEnabled.postValue(newValue)
        Log.d("MusicPlayerManager", "Shuffle ${if (newValue) "enabled" else "disabled"}")
    }

    fun toggleRepeat() {
        val currentMode = _repeatMode.value ?: RepeatMode.OFF
        val nextMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        _repeatMode.postValue(nextMode)
        Log.d("MusicPlayerManager", "Repeat mode changed to: $nextMode")
    }


    fun playNextSong() {
        viewModelScope.launch {
            val currentSong = _currentSong.value ?: return@launch
            if (_repeatMode.value == RepeatMode.REPEAT_ONE) {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                _isPlaying.postValue(true)
                return@launch
            }

            try {
                val allSongs = songRepository.getAllSongs().first()

                if (allSongs.isEmpty()) {
                    Log.d("MusicPlayerManager", "No songs available in repository")
                    return@launch
                }
                val currentIndex = allSongs.indexOfFirst { it.id == currentSong.id }
                val nextSong = when {
                    _isShuffleEnabled.value == true -> {
                        val availableSongs = if (allSongs.size > 1) {
                            allSongs.filter { it.id != currentSong.id }
                        } else {
                            allSongs
                        }
                        availableSongs.random()
                    }
                    currentIndex < allSongs.size - 1 -> allSongs[currentIndex + 1]
                    _repeatMode.value == RepeatMode.REPEAT_ALL -> allSongs.firstOrNull()
                    else -> null
                }
                nextSong?.let {
                    playSong(it)
                    Log.d("MusicPlayerManager", "Playing next song: ${it.title}")
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error playing next song", e)
            }
        }
    }


    fun playPreviousSong() {
        viewModelScope.launch {
            val currentSong = _currentSong.value ?: return@launch
            if (getCurrentPosition() > 3000) {
                mediaPlayer?.seekTo(0)
                return@launch
            }
            if (_repeatMode.value == RepeatMode.REPEAT_ONE) {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                _isPlaying.postValue(true)
                return@launch
            }

            try {
                val allSongs = songRepository.getAllSongs().first()

                if (allSongs.isEmpty()) {
                    Log.d("MusicPlayerManager", "No songs available in repository")
                    return@launch
                }
                val currentIndex = allSongs.indexOfFirst { it.id == currentSong.id }
                val previousSong = when {
                    _isShuffleEnabled.value == true -> {
                        val availableSongs = if (allSongs.size > 1) {
                            allSongs.filter { it.id != currentSong.id }
                        } else {
                            allSongs
                        }
                        availableSongs.random()
                    }
                    currentIndex > 0 -> allSongs[currentIndex - 1]
                    _repeatMode.value == RepeatMode.REPEAT_ALL -> allSongs.lastOrNull()
                    else -> null
                }
                previousSong?.let {
                    playSong(it)
                    Log.d("MusicPlayerManager", "Playing previous song: ${it.title}")
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error playing previous song", e)
            }
        }
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