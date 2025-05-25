package com.example.purrytify.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import com.example.purrytify.audio.AudioDevice
import com.example.purrytify.audio.AudioDeviceType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

    private val _isPlayerFragmentVisible = MutableStateFlow(false)
    val isPlayerFragmentVisible: StateFlow<Boolean> = _isPlayerFragmentVisible.asStateFlow()


    fun setPlayerFragmentVisible(isVisible: Boolean) {
        _isPlayerFragmentVisible.value = isVisible
        // Update notification when visibility changes
        currentSong.value?.let { updateNotificationService(it) }
    }
    private fun updateNotificationService(song: Song) {
        val intent = Intent(context, NotificationService::class.java).apply {
            action = NotificationService.ACTION_UPDATE
            putExtra("isPlayerVisible", _isPlayerFragmentVisible.value)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }


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
                stopPositionUpdates()
                releaseMediaPlayer()
                _currentPosition.postValue(0)



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

                        setOnPreparedListener {
                            start()
                            _isPlaying.postValue(true)
                            _songDuration.postValue(duration.toLong())
                            startPositionUpdates()
                            _isLoading.postValue(false)
                        }

                        setOnErrorListener { _, what, extra ->
                            Log.e("MusicPlayerManagerPlaySong", "Error $what, $extra")
                            _isPlaying.postValue(false)
                            _isLoading.postValue(false)
                            true
                        }

                        if (song.isLocal || song.isDownloaded) {
                            setDataSource(context, Uri.parse(song.path))
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

                        setOnCompletionListener {
//                            _isPlaying.postValue(false)
//                            stopPositionUpdates()
                            playNextSong()
                        }

//                        prepareAsync()
                    } catch (e: Exception) {
                        recoverFromError()
                        Log.e("MusicPlayerManager", "Error setting up MediaPlayer for path: ${song.path}", e)
//                        releaseMediaPlayer()
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
                if (!player.isPlaying) player.start()
                player.seekTo(position)
                _currentPosition.postValue(position)
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Seek error", e)
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = playerScope.launch {
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
        playerScope.cancel()
        releaseMediaPlayer()
        stopPositionUpdates()
        viewModelScope.cancel()
    }


    private fun recoverFromError() {
        releaseMediaPlayer()
        currentSong.value?.let { song ->
            playSong(song)
        }
    }

    private fun handleMediaPlayerError(what: Int, extra: Int) {
        playerScope.launch {
            stopPositionUpdates()
            releaseMediaPlayer()

        }
    }


    fun recreatePlayer(deviceInfo: AudioDeviceInfo) {
        val TAG = "MusicPlayerManager"
        Log.d(TAG, "Recreating media player for device: ${deviceInfo.productName}")

        try {
            val currentSong = _currentSong.value
            val currentPosition = mediaPlayer?.currentPosition ?: 0
            val wasPlaying = mediaPlayer?.isPlaying ?: false

            // Release current player
            Log.d(TAG, "Releasing current media player")
            releaseMediaPlayer()

            // Create new player with selected device
            if (currentSong != null) {
                Log.d(TAG, "Creating new media player for song: ${currentSong.title}")
                mediaPlayer = MediaPlayer().apply {
                    // Set audio attributes based on device type
                    val attributes = when (deviceInfo.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                            Log.d(TAG, "Setting attributes for internal speaker")
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        }
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            Log.d(TAG, "Setting attributes for Bluetooth")
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        }
                        else -> {
                            Log.d(TAG, "Setting default attributes")
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        }
                    }
                    setAudioAttributes(attributes)

                    // Force audio routing before setting data source
                    if (deviceInfo.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        Log.d(TAG, "Forcing speaker output")
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.clearCommunicationDevice()
                    } else {
                        Log.d(TAG, "Setting preferred device: ${deviceInfo.productName}")
                        setPreferredDevice(deviceInfo)
                    }

                    // Set data source
                    Log.d(TAG, "Setting data source: ${currentSong.path}")
                    if (currentSong.isLocal || currentSong.isDownloaded) {
                        setDataSource(context, Uri.parse(currentSong.path))
                    } else {
                        setDataSource(currentSong.path)
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "Media player error: what=$what, extra=$extra")
                        // Try to recover
                        when (what) {
                            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {

                                Handler(Looper.getMainLooper()).post {
                                    recreatePlayer(deviceInfo)
                                }
                            }
                        }
                        true // Indicate we handled the error
                    }

                    setOnPreparedListener {
                        Log.d(TAG, "Media player prepared")
                        seekTo(currentPosition)
                        if (wasPlaying) {
                            start()
                            _isPlaying.postValue(true)
                        }
                        // Verify audio output
                        val actualDevice = preferredDevice
                        Log.d(TAG, "Current audio device: ${actualDevice?.productName ?: "default"}")
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Media player error: $what, $extra")
                        false
                    }

                    prepareAsync()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating player", e)
            _isPlaying.postValue(false)
        }
    }

    fun setPreferredDevice(deviceInfo: AudioDeviceInfo): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.setPreferredDevice(deviceInfo) ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Failed to set preferred device", e)
            false
        }
    }

    fun getCurrentAudioDevice(): AudioDeviceInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.preferredDevice
        } else {
            null
        }
    }
}

