@file:OptIn(ExperimentalStdlibApi::class)
package com.example.purrytify.service


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.Settings.Global.putString
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject



@AndroidEntryPoint
class NotificationService : LifecycleService() {
    
    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager
    
    private lateinit var mediaSession: MediaSessionCompat
    private var notificationManager: NotificationManagerCompat? = null
    private val channelId = "purrytify_playback"
    private val notificationId = 1

    private var currentSongId: String? = null 

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        setupMediaSession()
//        observePlayback()
        startProgressUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun updatePlayerNotification(song: Song, isPlaying: Boolean) {
        // Only update song info if it's a different song
        if (currentSongId != song.id) {
            currentSongId = song.id
            updateNotificationMetadata(song)
        }
        
        // Always update playback state and progress
        updateNotificationPlayback(isPlaying)
        updateNotificationProgress()
    }

    private fun updateNotificationMetadata(song: Song) {
        val duration = musicPlayerManager.getDuration()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
            .setLargeIcon(loadAlbumArt(song.coverUrl))
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(formatTime(duration))
            .setContentIntent(createContentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Add shuffle button
            .addAction(
                if (musicPlayerManager.isShuffleEnabled.value == true)
                    R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                "Shuffle",
                createActionIntent(ACTION_SHUFFLE)
            )
            // Previous button
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            // Play/Pause button
            .addAction(
                if (musicPlayerManager.isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play,
                if (musicPlayerManager.isPlaying.value == true) "Pause" else "Play",
                createActionIntent(ACTION_PLAY_PAUSE)
            )
            // Next button
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
            .addAction(
                when (musicPlayerManager.repeatMode.value) {
                    MusicPlayerManager.RepeatMode.OFF -> R.drawable.ic_repeat
                    MusicPlayerManager.RepeatMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                    MusicPlayerManager.RepeatMode.REPEAT_ALL -> R.drawable.ic_repeat_all
                    else -> R.drawable.ic_repeat
                },
                "Repeat",
                createActionIntent(ACTION_REPEAT)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2,3,4,5)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        startForeground(notificationId, notification)
    }

    private fun updateNotificationPlayback(isPlaying: Boolean) {
        val currentSong = musicPlayerManager.currentSong.value ?: return
        val currentPosition = musicPlayerManager.getCurrentPosition()
        val duration = musicPlayerManager.getDuration()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
            .setLargeIcon(loadAlbumArt(currentSong.coverUrl))
            .setContentTitle(currentSong.title)
            .setContentText(currentSong.artist)
            .setSubText(formatTime(duration))
            .setContentIntent(createContentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Add shuffle button
            .addAction(
                if (musicPlayerManager.isShuffleEnabled.value == true)
                    R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                "Shuffle",
                createActionIntent(ACTION_SHUFFLE)
            )
            // Previous button
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            // Play/Pause button
            .addAction(

                if (musicPlayerManager.isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play,
                if (musicPlayerManager.isPlaying.value == true) "Pause" else "Play",
                createActionIntent(ACTION_PLAY_PAUSE)
            )
            // Next button
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
            .addAction(
                when (musicPlayerManager.repeatMode.value) {
                    MusicPlayerManager.RepeatMode.OFF -> R.drawable.ic_repeat
                    MusicPlayerManager.RepeatMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                    MusicPlayerManager.RepeatMode.REPEAT_ALL -> R.drawable.ic_repeat_all
                    else -> R.drawable.ic_repeat
                },
                "Repeat",
                createActionIntent(ACTION_REPEAT)
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2, 3, 4, 5) // Show shuffle, play/pause, next, repeat buttons
            )
            .setProgress(duration, currentPosition, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(isPlaying)
            .build()

        if (isPlaying) {
            startForeground(notificationId, notification)
        } else {
            notificationManager?.notify(notificationId, notification)
        }

//        notificationManager?.notify(notificationId, notification)
    }

    private fun updateNotificationProgress() {
        lifecycleScope.launch {
            while (true) {
                val currentSong = musicPlayerManager.currentSong.value
                val isPlaying = musicPlayerManager.isPlaying.value == true

                if (currentSong != null) {
                    val currentPosition = musicPlayerManager.getCurrentPosition()
                    val duration = musicPlayerManager.getDuration()
                    updatePlaybackState(currentPosition)

                    val metadataBuilder = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.artist)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())

                    mediaSession.setMetadata(metadataBuilder.build())

                    val notification = NotificationCompat.Builder(this@NotificationService, channelId)
                        .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
                        .setLargeIcon(loadAlbumArt(currentSong.coverUrl))
                        .setContentTitle(currentSong.title)
//                        .setContentText("${currentSong.artist}")
                        .setContentText("${currentSong.artist}")
                        .setSubText("${formatTime(currentPosition)} / ${formatTime(duration)}")
//                        .setSubText(formatTime(duration))
                        .setContentIntent(createContentIntent())
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(
                            if (musicPlayerManager.isShuffleEnabled.value == true) 
                                R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                            "Shuffle",
                            createActionIntent(ACTION_SHUFFLE)
                        )
                        .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
                        .addAction(
                            if (musicPlayerManager.isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play,
                            if (musicPlayerManager.isPlaying.value == true) "Pause" else "Play",
                            createActionIntent(ACTION_PLAY_PAUSE)
                        )
                        .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
                        .addAction(
                            when (musicPlayerManager.repeatMode.value) {
                                MusicPlayerManager.RepeatMode.OFF -> R.drawable.ic_repeat
                                MusicPlayerManager.RepeatMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                                MusicPlayerManager.RepeatMode.REPEAT_ALL -> R.drawable.ic_repeat_all
                                else -> R.drawable.ic_repeat
                            },
                            "Repeat",
                            createActionIntent(ACTION_REPEAT)
                        )
                        .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
                        .setStyle(
                            MediaStyle()
                                .setMediaSession(mediaSession.sessionToken)
                                .setShowActionsInCompactView(0, 1, 2, 3, 4, 5) // Show shuffle, play/pause, next, repeat buttons
                        )
                        .setProgress(duration, currentPosition, false)
//                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setOngoing(true)
                        .build()



                    if (isPlaying) {
                        startForeground(notificationId, notification)
                    } else {
                        notificationManager?.notify(notificationId, notification)
                        // Instead of continue, use return@launch for inline lambda
                        if (!isPlaying) {
                            delay(1000)
                            return@launch
                        }
                    }

                }
                delay(if (isPlaying) 1000 else 500)
            }
        }
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Improve time formatting to handle longer durations
    private fun formatTime(millis: Int): String {
        val hours = millis / 1000 / 60 / 60
        val minutes = millis / 1000 / 60 % 60
        val seconds = millis / 1000 % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    private var progressUpdateJob: Job? = null

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            while (true) {
                musicPlayerManager.currentSong.value?.let { song ->
                    val isPlaying = musicPlayerManager.isPlaying.value == true
                    if (isPlaying) {
                        updatePlayerNotification(song, true)
                    } else {
                        // Update once when paused to show correct time
                        updatePlayerNotification(song, false)
                        delay(1000) // Wait longer when paused

                    }
                }
                delay(1000)
            }
        }
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, NotificationService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            when(action) {
                ACTION_PREVIOUS -> 0
                ACTION_PLAY_PAUSE -> 1
                ACTION_NEXT -> 2
                else -> 3
            },
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "PurrytifySession").apply {
            // Remove FLAG_HANDLES_SEEK as it's not needed
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_STOP or
                                PlaybackStateCompat.ACTION_SEEK_TO or
                                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                    )
                    .setState(
                        PlaybackStateCompat.STATE_NONE,
                        0,
                        1.0f,
                        SystemClock.elapsedRealtime()
                    )
                    .build()
            )


            // Update metadata with duration
            val metadataBuilder = MediaMetadataCompat.Builder().apply {
                val currentSong = musicPlayerManager.currentSong.value
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong?.title)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong?.artist)
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, musicPlayerManager.getDuration().toLong())
            }
            setMetadata(metadataBuilder.build())
            
            // Set initial playback state with seek capability
            setPlaybackState(createPlaybackState(0))
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    musicPlayerManager.togglePlayPause()
                }

                override fun onPause() {
                    musicPlayerManager.togglePlayPause()
                }

                override fun onSkipToNext() {
                    musicPlayerManager.playNextSong()
                }

                override fun onSkipToPrevious() {
                    musicPlayerManager.playPreviousSong()
                }

                override fun onStop() {
                    musicPlayerManager.release()
                    stopForeground(true)
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    musicPlayerManager.seekTo(pos.toInt())
                    updatePlaybackState(pos.toInt())
                }

                override fun onSetShuffleMode(shuffleMode: Int) {
                    musicPlayerManager.toggleShuffle()
                }

                override fun onSetRepeatMode(repeatMode: Int) {
                    musicPlayerManager.toggleRepeat()
                }
            })
        }
    }

    private fun createPlaybackState(currentPosition: Int): PlaybackStateCompat {
        val state = when {
            musicPlayerManager.isPlaying.value == true -> PlaybackStateCompat.STATE_PLAYING
            currentPosition > 0 -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }

        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                state,
                currentPosition.toLong(),
                1f,
                android.os.SystemClock.elapsedRealtime()
            )
            .build()
    }



    private fun loadAlbumArt(url: String?): Bitmap? {
        return try {
            Glide.with(this)
                .asBitmap()
                .load(url)
                .submit(144, 144)
                .get()
        } catch (e: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when(intent?.action) {
            ACTION_UPDATE -> {
                val isPlayerVisible = intent.getBooleanExtra("isPlayerVisible", false)
                if (isPlayerVisible) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    musicPlayerManager.currentSong.value?.let {
                        updateNotificationPlayback(musicPlayerManager.isPlaying.value == true)
                    }
                }
            }
            ACTION_PLAY_PAUSE -> musicPlayerManager.togglePlayPause()
            ACTION_NEXT -> musicPlayerManager.playNextSong()
            ACTION_PREVIOUS -> musicPlayerManager.playPreviousSong()
            ACTION_STOP -> {
                musicPlayerManager.release()
                stopForeground(true)
                stopSelf()
            }
            ACTION_SHUFFLE -> musicPlayerManager.toggleShuffle()
            ACTION_REPEAT -> musicPlayerManager.toggleRepeat()
        }
        return START_NOT_STICKY
    }



//    private fun observePlayback() {
//        musicPlayerManager.currentSong.observe(this) { song ->
//            song?.let {
//                updatePlayerNotification(it, musicPlayerManager.isPlaying.value ?: false)
//            }
//        }
//
//        musicPlayerManager.isPlaying.observe(this) { isPlaying ->
//            musicPlayerManager.currentSong.value?.let {
//                updatePlayerNotification(it, isPlaying)
//            }
//        }
//    }
    override fun onDestroy() {
        super.onDestroy()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    private fun updatePlaybackState(currentPosition: Int) {
        val state = if (musicPlayerManager.isPlaying.value == true) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                state,
                currentPosition.toLong(),
                1.0f
            )
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
        const val ACTION_SHUFFLE = "action_shuffle"
        const val ACTION_REPEAT = "action_repeat"
        const val ACTION_UPDATE = "action_update"
    }
}