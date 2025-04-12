package com.example.purrytify.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayer @Inject constructor(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentSong = MutableLiveData<Song?>(null)
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _songDuration = MutableLiveData(0L)
    val songDuration: LiveData<Long> = _songDuration

    fun playSong(song: Song) {
        _currentSong.value = song
        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(song.path))
                setOnPreparedListener {
                    start()
                    _isPlaying.value = true
                    _songDuration.value = duration.toLong()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    // Auto play next song if available
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicPlayer", "MediaPlayer error: $what, $extra")
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error setting up MediaPlayer", e)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.start()
                _isPlaying.value = true
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position.toLong()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
}