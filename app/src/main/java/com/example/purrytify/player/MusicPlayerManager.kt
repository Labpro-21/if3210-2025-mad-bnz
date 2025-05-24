package com.example.purrytify.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.media.AudioAttributes
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.PlaylistRecommendation
import com.example.purrytify.model.Song
import com.example.purrytify.repository.AnalyticsRepository
import com.example.purrytify.repository.OnlineSongRepository
import com.example.purrytify.repository.SongRepository
import com.example.purrytify.service.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val onlineSongRepository: OnlineSongRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val tokenManager: TokenManager
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false
    private var positionUpdateJob: Job? = null
    private val BUFFER_SIZE = 16 * 1024 * 1024
    private var isPreparing = false

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Int>(0)
    val currentPosition: LiveData<Int> = _currentPosition

    private val _songDuration = MutableLiveData<Long>(0)
    val songDuration: LiveData<Long> = _songDuration

    private val _playlist = mutableListOf<Song>()
    private var currentIndex = -1

    private val _currentPlaylist = MutableLiveData<List<Song>>()
    val currentPlaylist: LiveData<List<Song>> = _currentPlaylist

    private enum class PlaylistSource {
        OFFLINE,
        GLOBAL_TOP,
        COUNTRY_TOP
    }

    private var currentPlaylistSource = PlaylistSource.OFFLINE

    private val _isShuffleEnabled = MutableLiveData<Boolean>(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.OFF)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private var shuffledPlaylist: List<Song> = emptyList()
    private var originalPlaylist: List<Song> = emptyList()

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var cachedPlaylist: List<Song> = emptyList()

    enum class RepeatMode {
        OFF, REPEAT_ALL, REPEAT_ONE
    }

    private var retryCount = 0
    private val MAX_RETRY_ATTEMPTS = 3


    private var currentRecommendedPlaylist: PlaylistRecommendation? = null
    private var notificationService: NotificationService? = null

    fun playRecommendedPlaylist(playlist: PlaylistRecommendation, startSongIndex: Int = 0) {
        currentRecommendedPlaylist = playlist
        val songs = playlist.songs

        if (songs.isNotEmpty() && startSongIndex < songs.size) {
            _currentPlaylist.postValue(songs)
            playSong(songs[startSongIndex])
        }
    }


    private fun getCurrentUserId(): String {
        return tokenManager.getUserId() ?: throw IllegalStateException("User not logged in")
    }

    init {
        // Load initial state from local storage
        viewModelScope.launch {
            loadInitialState()
        }
    }
    private suspend fun loadInitialState() {
        try {
            // Load online songs if available
            onlineSongRepository.getGlobalTopSongs().collect { songs ->
                songRepository.addOrUpdateOnlineSongs(songs)
            }
            Log.e("COUNTRYY",tokenManager.getUserCountry().toString())
            onlineSongRepository.getCountryTopSongs(tokenManager.getUserCountry().toString()).collect { songs ->
                songRepository.addOrUpdateOnlineSongs(songs)
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error loading online songs", e)
        }
    }

    fun initialize() {
        if (!isInitialized) {
            isInitialized = true
            Log.d("MusicPlayerManager", "Initialized")
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                releaseMediaPlayer()
                _currentPosition.postValue(0)
                stopPositionUpdates()


                // Update playlist source
                currentPlaylistSource = when {
                    song.isLocal || song.isDownloaded -> PlaylistSource.OFFLINE
                    song.country == "GLOBAL" -> PlaylistSource.GLOBAL_TOP
                    else -> PlaylistSource.COUNTRY_TOP
                }
                updateCurrentPlaylist()

                _currentSong.postValue(song)
                isPreparing = true

                mediaPlayer = MediaPlayer().apply {
                    try {
                        setOnBufferingUpdateListener { _, percent ->
                            if (percent < 100) {
                                _isLoading.postValue(true)
                            } else {
                                _isLoading.postValue(false)
                            }
                        }
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )

                        setOnErrorListener { mp, what, extra ->
                            Log.e("MusicPlayerManager", "MediaPlayer error: $what, $extra for path: ${song.path}")

                            if (retryCount < MAX_RETRY_ATTEMPTS) {
                                retryCount++
                                viewModelScope.launch {
                                    delay(1000) // Wait a second before retrying
                                    playSong(song) // Retry playback
                                }
                                true
                            } else {
                                retryCount = 0
                                isPreparing = false
                                _isPlaying.postValue(false)
                                releaseMediaPlayer()
                                true
                            }
                        }

                        if (song.isLocal || song.isDownloaded) {
//                            val file = File(song.path)
//                            if (file.exists()) {
                            setDataSource(context, Uri.parse(song.path))
//                            } else {
//                                throw IOException("Local file not found")
//                            }
                        } else {
                            setDataSource(song.path)
                        }

                        if (!song.isLocal && !song.isDownloaded) {
                            val headers = HashMap<String, String>()
                            headers["User-Agent"] = "PurrytifyPlayer/1.0"
                            headers["Connection"] = "keep-alive"
                            headers["Cache-Control"] = "no-cache"
                        }

                        prepareAsync()

                        setOnPreparedListener {
                            retryCount = 0
                            isPreparing = false
                            start()
                            _isPlaying.postValue(true)
                            _songDuration.postValue(duration.toLong())
                            startPositionUpdates()

                            if (song.isLocal || song.isDownloaded) {
                                viewModelScope.launch {
                                    songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
                                }
                            }
                        }

                        setOnCompletionListener {
                            _isPlaying.postValue(false)
                            stopPositionUpdates()
                            playNextSong()
                        }

//                        prepareAsync()
                    } catch (e: Exception) {
                        Log.e("MusicPlayerManager", "Error setting up MediaPlayer for path: ${song.path}", e)
                        releaseMediaPlayer()
                        throw e
                    }
                }

                // Start notification service
                startNotificationService(song)

                // Update notification
                notificationService?.updatePlayerNotification(song, _isPlaying.value == true)

                // Observe playing state changes for notification
                _isPlaying.observeForever { isPlaying ->
                    notificationService?.updatePlayerNotification(song, isPlaying)
                }

            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error playing song: ${song.title}", e)
                isPreparing = false
                _isPlaying.postValue(false)
                _currentPosition.postValue(0)
                stopPositionUpdates()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private fun startNotificationService(song: Song) {
        val serviceIntent = Intent(context, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private suspend fun updateCurrentPlaylist() {
        try {
            val songs = when {
                currentRecommendedPlaylist != null -> currentRecommendedPlaylist?.songs ?: emptyList()
                else -> when (currentPlaylistSource) {
                    PlaylistSource.OFFLINE -> songRepository.getOfflineSongs()
                    PlaylistSource.GLOBAL_TOP -> songRepository.getGlobalTopSongs()
                    PlaylistSource.COUNTRY_TOP -> songRepository.getCountryTopSongs()
                }.first()
            }

            Log.d("MusicPlayerManager", "Updating playlist - Source: $currentPlaylistSource, Size: ${songs.size}")

            if (songs.isNotEmpty()) {
                cachedPlaylist = songs
                _currentPlaylist.postValue(songs)
            } else {
                // If new playlist is empty but we have cached songs, keep using cached
                if (cachedPlaylist.isNotEmpty()) {
                    Log.d("MusicPlayerManager", "Using cached playlist - Size: ${cachedPlaylist.size}")
                    _currentPlaylist.postValue(cachedPlaylist)
                } else {
                    Log.e("MusicPlayerManager", "Both current and cached playlists are empty")
                    _currentPlaylist.postValue(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error updating playlist: ${e.message}")
            if (cachedPlaylist.isNotEmpty()) {
                Log.d("MusicPlayerManager", "Error occurred, using cached playlist")
                _currentPlaylist.postValue(cachedPlaylist)
            } else {
                _currentPlaylist.postValue(emptyList())
            }
        }
    }

//    fun toggleShuffle() {
//        val newValue = !(_isShuffleEnabled.value ?: false)
//        _isShuffleEnabled.postValue(newValue)
//        Log.d("MusicPlayerManager", "Shuffle ${if (newValue) "enabled" else "disabled"}")
//    }

    fun toggleShuffle() {
        val newShuffleState = !(_isShuffleEnabled.value ?: false)
        _isShuffleEnabled.postValue(newShuffleState)

        if (newShuffleState) {
            originalPlaylist = _currentPlaylist.value ?: emptyList()
            shuffledPlaylist = originalPlaylist.shuffled()
        } else {
            shuffledPlaylist = emptyList()
            _currentPlaylist.postValue(originalPlaylist)
        }
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
        val currentSong = _currentSong.value ?: return
        val playlist = if (_isShuffleEnabled.value == true) shuffledPlaylist else _currentPlaylist.value
        val currentIndex = playlist?.indexOf(currentSong) ?: -1

        if (currentIndex != -1 && playlist != null) {
            when (_repeatMode.value) {
                RepeatMode.REPEAT_ONE -> {
                    playSong(currentSong)
                }
                RepeatMode.REPEAT_ALL -> {
                    val nextIndex = (currentIndex + 1) % playlist.size
                    playSong(playlist[nextIndex])
                }
                RepeatMode.OFF -> {
                    if (currentIndex < playlist.size - 1) {
                        playSong(playlist[currentIndex + 1])
                    }
                }
                else -> {
                    if (currentIndex < playlist.size - 1) {
                        playSong(playlist[currentIndex + 1])
                    }
                }
            }
        }
    }



    fun playPreviousSong() {
        val currentSong = _currentSong.value ?: return
        val playlist = if (_isShuffleEnabled.value == true) shuffledPlaylist else _currentPlaylist.value
        val currentIndex = playlist?.indexOf(currentSong) ?: -1

        if (currentIndex != -1 && playlist != null) {
            when (_repeatMode.value) {
                RepeatMode.REPEAT_ONE -> {
                    playSong(currentSong)
                }
                RepeatMode.REPEAT_ALL -> {
                    val previousIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                    playSong(playlist[previousIndex])
                }
                RepeatMode.OFF -> {
                    if (currentIndex > 0) {
                        playSong(playlist[currentIndex - 1])
                    }
                }
                else -> {
                    if (currentIndex > 0) {
                        playSong(playlist[currentIndex - 1])
                    }
                }
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
        try {
            mediaPlayer?.let { player ->
                if (!isPreparing) {
                    player.seekTo(position)
                    _currentPosition.postValue(position)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error seeking to position", e)
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            val currentTime = System.currentTimeMillis()
                            val playedDuration = currentTime - lastUpdateTime

                            // Update analytics every second
                            if (playedDuration >= 1000) {
                                _currentSong.value?.let { song ->
                                    analyticsRepository.logPlayback(
                                        songId = song.id,
                                        duration = playedDuration,
                                        userId = getCurrentUserId()
                                    )
                                }
                                lastUpdateTime = currentTime
                            }

                            _currentPosition.postValue(player.currentPosition)
                        }
                    }
                    delay(100)
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
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()

            }
            mediaPlayer = null
            _isPlaying.postValue(false)
            stopPositionUpdates()

        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error releasing media player", e)
        }
    }



    fun release() {
        viewModelScope.cancel()
        stopPositionUpdates()
        releaseMediaPlayer()
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayerManager", "Error getting current position", e)
            0
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayerManager", "Error getting duration", e)
            0
        }
    }
    fun cleanup() {
        notificationService?.stopSelf()
        releaseMediaPlayer()
        stopPositionUpdates()
        viewModelScope.cancel()
    }


}