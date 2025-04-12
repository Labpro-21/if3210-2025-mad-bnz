package com.example.purrytify.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
//    private val context: Context
) {
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


    fun initialize() {
        if (!isInitialized) {
            isInitialized = true
            Log.d("MusicPlayerManager", "Initialized")
        }
    }

    fun playSong(song: Song) {
        _currentSong.postValue(song)

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