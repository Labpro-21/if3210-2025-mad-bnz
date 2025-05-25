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
import androidx.lifecycle.Observer
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
import java.lang.Thread.sleep
import java.net.URL
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

//    fun playSong(song: Song) {
//        viewModelScope.launch {
//            try {
//                _isLoading.postValue(true)
//                stopPositionUpdates()
//                releaseMediaPlayer()
//                _currentPosition.postValue(0)
//
//
//
//                // Update playlist source
//                currentPlaylistSource = when {
//                    song.isLocal || song.isDownloaded -> PlaylistSource.OFFLINE
//                    song.country == "GLOBAL" -> PlaylistSource.GLOBAL_TOP
//                    else -> PlaylistSource.COUNTRY_TOP
//                }
//                updateCurrentPlaylist()
//
//                _currentSong.postValue(song)
//                isPreparing = true
//
//                mediaPlayer = MediaPlayer().apply {
//                    try {
//                        setOnBufferingUpdateListener { _, percent ->
//                            if (percent < 100) {
//                                _isLoading.postValue(true)
//                            } else {
//                                _isLoading.postValue(false)
//                            }
//                        }
//                        setAudioAttributes(
//                            AudioAttributes.Builder()
//                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                                .setUsage(AudioAttributes.USAGE_MEDIA)
//                                .build()
//                        )
//
//                        setOnPreparedListener {
//                            start()
//                            _isPlaying.postValue(true)
//                            _songDuration.postValue(duration.toLong())
//                            startPositionUpdates()
//                            _isLoading.postValue(false)
//                        }
//
//                        setOnErrorListener { _, what, extra ->
//                            Log.e("MusicPlayerManagerPlaySong", "Error $what, $extra")
//                            _isPlaying.postValue(false)
//                            _isLoading.postValue(false)
//                            true
//                        }
//
//                        if (song.isLocal || song.isDownloaded) {
//                            setDataSource(context, Uri.parse(song.path))
//                        } else {
//                            setDataSource(song.path)
//                        }
//
//                        if (!song.isLocal && !song.isDownloaded) {
//                            val headers = HashMap<String, String>()
//                            headers["User-Agent"] = "PurrytifyPlayer/1.0"
//                            headers["Connection"] = "keep-alive"
//                            headers["Cache-Control"] = "no-cache"
//                        }
//
//                        prepareAsync()
//
//                        setOnCompletionListener {
////                            _isPlaying.postValue(false)
////                            stopPositionUpdates()
//                            playNextSong()
//                        }
//
////                        prepareAsync()
//                    } catch (e: Exception) {
//                        recoverFromError()
//                        Log.e("MusicPlayerManager", "Error setting up MediaPlayer for path: ${song.path}", e)
////                        releaseMediaPlayer()
//                        throw e
//                    }
//                }
//
//                // Start notification service
//                startNotificationService(song)
//
//                // Update notification
//                notificationService?.updatePlayerNotification(song, _isPlaying.value == true)
//
//                // Observe playing state changes for notification
//                _isPlaying.observeForever { isPlaying ->
//                    notificationService?.updatePlayerNotification(song, isPlaying)
//                }
//
//            } catch (e: Exception) {
//                Log.e("MusicPlayerManager", "Error playing song: ${song.title}", e)
//                isPreparing = false
//                _isPlaying.postValue(false)
//                _currentPosition.postValue(0)
//                stopPositionUpdates()
//            } finally {
//                _isLoading.postValue(false)
//            }
//        }
//    }

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

//    private fun startPositionUpdates() {
//        positionUpdateJob?.cancel()
//        positionUpdateJob = playerScope.launch {
//            var lastUpdateTime = System.currentTimeMillis()
//            while (isActive) {
//                try {
//                    mediaPlayer?.let { player ->
//                        if (player.isPlaying) {
//                            val currentTime = System.currentTimeMillis()
//                            val playedDuration = currentTime - lastUpdateTime
//                            // Update analytics every second
//                            if (playedDuration >= 1000) {
//                                _currentSong.value?.let { song ->
//                                    analyticsRepository.logPlayback(
//                                        songId = song.id,
//                                        duration = playedDuration,
//                                        userId = getCurrentUserId()
//                                    )
//                                }
//                                lastUpdateTime = currentTime
//                            }
//
//                            _currentPosition.postValue(player.currentPosition)
//                        }
//                    }
//                    delay(100)
//                } catch (e: Exception) {
//                    Log.e("MusicPlayerManager", "Error updating position", e)
//                }
//            }
//        }
//    }

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
        stopPositionUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
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
//    fun cleanup() {
//        notificationService?.stopSelf()
//        playerScope.cancel()
//        releaseMediaPlayer()
//        stopPositionUpdates()
//        viewModelScope.cancel()
//    }


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


//    fun recreatePlayer(deviceInfo: AudioDeviceInfo) {
//        val TAG = "MusicPlayerManager"
//        Log.d(TAG, "Recreating media player for device: ${deviceInfo.productName}")
//
//        try {
//            val currentSong = _currentSong.value
//            val currentPosition = mediaPlayer?.currentPosition ?: 0
//            val wasPlaying = mediaPlayer?.isPlaying ?: false
//
//            // Release current player
//            Log.d(TAG, "Releasing current media player")
//            releaseMediaPlayer()
//
//            // Create new player with selected device
//            if (currentSong != null) {
//                Log.d(TAG, "Creating new media player for song: ${currentSong.title}")
//                mediaPlayer = MediaPlayer().apply {
//                    // Set audio attributes based on device type
//                    val attributes = when (deviceInfo.type) {
//                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
//                            Log.d(TAG, "Setting attributes for internal speaker")
//                            AudioAttributes.Builder()
//                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                                .setUsage(AudioAttributes.USAGE_MEDIA)
//                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
//                                .build()
//                        }
//                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
//                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
//                            Log.d(TAG, "Setting attributes for Bluetooth")
//                            AudioAttributes.Builder()
//                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                                .setUsage(AudioAttributes.USAGE_MEDIA)
//                                .build()
//                        }
//                        else -> {
//                            Log.d(TAG, "Setting default attributes")
//                            AudioAttributes.Builder()
//                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                                .setUsage(AudioAttributes.USAGE_MEDIA)
//                                .build()
//                        }
//                    }
//                    setAudioAttributes(attributes)
//
//                    // Force audio routing before setting data source
//                    if (deviceInfo.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
//                        Log.d(TAG, "Forcing speaker output")
//                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//                        audioManager.clearCommunicationDevice()
//                    } else {
//                        Log.d(TAG, "Setting preferred device: ${deviceInfo.productName}")
//                        setPreferredDevice(deviceInfo)
//                    }
//
//                    // Set data source
//                    Log.d(TAG, "Setting data source: ${currentSong.path}")
//                    if (currentSong.isLocal || currentSong.isDownloaded) {
//                        setDataSource(context, Uri.parse(currentSong.path))
//                    } else {
//                        setDataSource(currentSong.path)
//                    }
//                    setOnErrorListener { mp, what, extra ->
//                        Log.e(TAG, "Media player error: what=$what, extra=$extra")
//                        // Try to recover
//                        when (what) {
//                            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
//
//                                Handler(Looper.getMainLooper()).post {
//                                    recreatePlayer(deviceInfo)
//                                }
//                            }
//                        }
//                        true // Indicate we handled the error
//                    }
//
//                    setOnPreparedListener {
//                        Log.d(TAG, "Media player prepared")
//                        seekTo(currentPosition)
//                        if (wasPlaying) {
//                            start()
//                            _isPlaying.postValue(true)
//                        }
//                        // Verify audio output
//                        val actualDevice = preferredDevice
//                        Log.d(TAG, "Current audio device: ${actualDevice?.productName ?: "default"}")
//                    }
//
//                    setOnErrorListener { _, what, extra ->
//                        Log.e(TAG, "Media player error: $what, $extra")
//                        false
//                    }
//
//                    prepareAsync()
//                }
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error recreating player", e)
//            _isPlaying.postValue(false)
//        }
//    }

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



//    test algo

    private fun createMediaPlayer(): MediaPlayer {
        return MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )


            setOnErrorListener { mp, what, extra ->
                Log.e("MusicPlayerManager", "MediaPlayer error: what=$what, extra=$extra")

                when (what) {
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                        Log.e("MusicPlayerManager", "Server died, attempting recovery")
                        handleServerDiedError()
                    }
                    MediaPlayer.MEDIA_ERROR_IO -> {
                        Log.e("MusicPlayerManager", "Network IO error")
                        handleNetworkError()
                    }
                    MediaPlayer.MEDIA_ERROR_MALFORMED -> {
                        Log.e("MusicPlayerManager", "Malformed media")
                        skipToNextSong()
                    }
                    else -> {
                        Log.e("MusicPlayerManager", "Unknown error, attempting recovery")
                        handleGenericError()
                    }
                }

                true // Always return true to prevent crashes
            }

            setOnPreparedListener { mp ->
                try {
                    if (mp == mediaPlayer) { // Ensure this is still the current player
                        mp.start()
                        _isPlaying.postValue(true)
                        _songDuration.postValue(mp.duration.toLong())
                        startPositionUpdates()
                        _isLoading.postValue(false)

                        Log.d("MusicPlayerManager", "MediaPlayer prepared and started successfully")
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error in onPrepared", e)
                    handleGenericError()
                }
            }

            setOnCompletionListener { mp ->
                try {
                    if (mp == mediaPlayer) { // Ensure this is still the current player
                        Log.d("MusicPlayerManager", "Song completed, playing next")
                        playNextSong()
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error in onCompletion", e)
                }
            }

            setOnBufferingUpdateListener { mp, percent ->
                if (mp == mediaPlayer) {
                    _isLoading.postValue(percent < 100)
                }
            }
        }
    }
    fun playSong(song: Song) {

//        togglePlayPause()
        release()
        sleep(1000)
//        stopPositionUpdates()

        viewModelScope.launch {
            try {
                synchronized(this@MusicPlayerManager) {
                    if (isPreparing) {
                        Log.w("MusicPlayerManager", "Already preparing, ignoring new request")
                        return@launch
                    }
                    isPreparing = true
                }

                _isLoading.postValue(true)
                _currentPosition.postValue(0)


                releaseMediaPlayerSafe()


                updatePlaylistSource(song)
                _currentSong.postValue(song)


                mediaPlayer = createMediaPlayer()

                mediaPlayer?.let { player ->
                    try {

                        if (song.isLocal || song.isDownloaded) {
                            player.setDataSource(context, Uri.parse(song.path))
                        } else {

                            val url = URL(song.path)
                            val connection = url.openConnection()
                            connection.setRequestProperty("User-Agent", "PurrytifyPlayer/1.0")
                            connection.setRequestProperty("Connection", "keep-alive")
                            player.setDataSource(context, Uri.parse(song.path))
                        }


                        player.prepareAsync()


                        startNotificationService(song)

                    } catch (e: IOException) {
                        Log.e("MusicPlayerManager", "IOException setting data source", e)
                        handleNetworkError()
                    } catch (e: Exception) {
                        Log.e("MusicPlayerManager", "Error setting up MediaPlayer", e)
                        handleGenericError()
                    }
                }

            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Critical error in playSong", e)
                handleCriticalError()
            } finally {
                synchronized(this@MusicPlayerManager) {
                    isPreparing = false
                }
            }
        }
    }


    private fun releaseMediaPlayerSafe() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error releasing MediaPlayer safely", e)
        } finally {
            mediaPlayer = null
            _isPlaying.postValue(false)
        }
    }


    private fun handleServerDiedError() {
        viewModelScope.launch {
            try {
                delay(1000) // Wait before retry
                val currentSong = _currentSong.value
                if (currentSong != null) {
                    Log.d("MusicPlayerManager", "Retrying after server died")
                    playSong(currentSong)
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Failed to recover from server died", e)
                handleCriticalError()
            }
        }
    }

    private fun handleNetworkError() {
        viewModelScope.launch {
            _isLoading.postValue(false)
            _isPlaying.postValue(false)


            val currentSong = _currentSong.value
            if (currentSong != null && !currentSong.isLocal && !currentSong.isDownloaded) {
                Log.d("MusicPlayerManager", "Network error, skipping online song")
                delay(500)
                playNextSong()
            }
        }
    }

    private fun skipToNextSong() {
        viewModelScope.launch {
            Log.d("MusicPlayerManager", "Skipping corrupted song")
            delay(500)
            playNextSong()
        }
    }

    private fun handleGenericError() {
        viewModelScope.launch {
            _isLoading.postValue(false)
            _isPlaying.postValue(false)


            val currentSong = _currentSong.value
            if (currentSong != null) {
                delay(1000)
                try {
                    playSong(currentSong)
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Recovery failed, skipping", e)
                    playNextSong()
                }
            }
        }
    }

    private fun handleCriticalError() {
        _isLoading.postValue(false)
        _isPlaying.postValue(false)
        _currentPosition.postValue(0)


        Log.e("MusicPlayerManager", "Critical error occurred, player stopped")
    }


//    private fun startPositionUpdates() {
//        positionUpdateJob?.cancel()
//        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
//            var lastUpdateTime = System.currentTimeMillis()
//            while (isActive && mediaPlayer != null) {
//                try {
//                    val player = mediaPlayer
//                    if (player != null && player.isPlaying) {
//                        val currentTime = System.currentTimeMillis()
//                        val playedDuration = currentTime - lastUpdateTime
//
//
//                        if (playedDuration >= 1000) {
//                            _currentSong.value?.let { song ->
//                                try {
//                                    analyticsRepository.logPlayback(
//                                        songId = song.id,
//                                        duration = playedDuration,
//                                        userId = getCurrentUserId()
//                                    )
//                                } catch (e: Exception) {
//                                    Log.e("MusicPlayerManager", "Analytics error", e)
//                                }
//                            }
//                            lastUpdateTime = currentTime
//                        }
//
//
//                        _currentPosition.postValue(player.currentPosition)
//                    } else {
//                        delay(500)
//                    }
//
//                    delay(100)
//                } catch (e: Exception) {
//                    Log.e("MusicPlayerManager", "Error in position update", e)
//                    delay(500)
//                }
//            }
//        }
//    }

    private fun startPositionUpdates() {
        // Cancel existing job first
        stopPositionUpdates()

        // Use playerScope instead of creating new scope
        positionUpdateJob = playerScope.launch {
            var lastAnalyticsUpdate = System.currentTimeMillis()

            while (isActive) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        // Update position every 1 second
                        _currentPosition.postValue(player.currentPosition)

                        // Log analytics every 30 seconds
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAnalyticsUpdate >= 30000) { // 30 seconds
                            _currentSong.value?.let { song ->
                                try {
                                    analyticsRepository.logPlayback(
                                        songId = song.id,
                                        duration = currentTime - lastAnalyticsUpdate,
                                        userId = getCurrentUserId()
                                    )
                                    lastAnalyticsUpdate = currentTime
                                } catch (e: Exception) {
                                    
                                }
                            }
                        }
                    }

                    // Update every 1 second instead of 100ms
                    delay(1000)

                } catch (e: Exception) {

                    // If error occurs, wait longer before retry
                    delay(2000)
                }
            }
        }
    }

    private var notificationObserver: Observer<Boolean>? = null

    private fun setupNotificationObserver(song: Song) {

        notificationObserver?.let { observer ->
            _isPlaying.removeObserver(observer)
        }


        notificationObserver = Observer { isPlaying ->
            try {
                notificationService?.updatePlayerNotification(song, isPlaying)
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Notification update error", e)
            }
        }

        _isPlaying.observeForever(notificationObserver!!)
    }


    fun recreatePlayer(deviceInfo: AudioDeviceInfo) {
        val TAG = "MusicPlayerManager"
        Log.d(TAG, "Recreating media player for device: ${deviceInfo.productName}")

        viewModelScope.launch {
            try {
                val currentSong = _currentSong.value ?: return@launch
                val currentPosition = mediaPlayer?.currentPosition ?: 0
                val wasPlaying = mediaPlayer?.isPlaying ?: false


                stopPositionUpdates()


                releaseMediaPlayerSafe()


                mediaPlayer = createMediaPlayer().apply {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            setPreferredDevice(deviceInfo)
                            Log.d(TAG, "Set preferred device: ${deviceInfo.productName}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set preferred device", e)
                        }
                    }
                    setOnPreparedListener { mp ->
                        try {
                            mp.seekTo(currentPosition)
                            if (wasPlaying) {
                                mp.start()
                                _isPlaying.postValue(true)
                                startPositionUpdates()
                            }
                            _songDuration.postValue(mp.duration.toLong())

                            Log.d(TAG, "Player recreated successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in recreated player onPrepared", e)
                        }
                    }
                }
                if (currentSong.isLocal || currentSong.isDownloaded) {
                    mediaPlayer?.setDataSource(context, Uri.parse(currentSong.path))
                } else {
                    mediaPlayer?.setDataSource(currentSong.path)
                }

                mediaPlayer?.prepareAsync()

            } catch (e: Exception) {
                Log.e(TAG, "Error recreating player", e)
                handleGenericError()
            }
        }
    }

    fun cleanup() {
        notificationObserver?.let { observer ->
            _isPlaying.removeObserver(observer)
        }
        notificationObserver = null

        viewModelScope.cancel()
        playerScope.cancel()

        notificationService?.stopSelf()

        stopPositionUpdates()
        releaseMediaPlayerSafe()
    }

    private fun updatePlaylistSource(song: Song) {
        currentPlaylistSource = when {
            song.isLocal || song.isDownloaded -> PlaylistSource.OFFLINE
            song.country == "GLOBAL" -> PlaylistSource.GLOBAL_TOP
            else -> PlaylistSource.COUNTRY_TOP
        }

        viewModelScope.launch {
            updateCurrentPlaylist()
        }
    }
}

