package com.example.purrytify.player

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayer @Inject constructor(context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null

    val isPlaying = MutableLiveData(false)
    val currentPosition = MutableLiveData(0L)
    val songDuration = MutableLiveData(0L)

    fun playSong(song: Song) {
        currentSong = song
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(song.path)
            setOnPreparedListener {
                start()
                isPlaying.value = true
                songDuration.value = duration.toLong()
            }
            setOnCompletionListener {
                isPlaying.value = false
                // Auto play next song if available
            }
            prepareAsync()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying.value = false
            } else {
                player.start()
                isPlaying.value = true
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying.value = false
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
}