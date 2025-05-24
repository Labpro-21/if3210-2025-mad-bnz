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
import android.support.v4.media.session.MediaSessionCompat
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
        observePlayback()
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
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
            .setLargeIcon(loadAlbumArt(song.coverUrl))
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(createContentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            .addAction(
                if (musicPlayerManager.isPlaying.value == true) R.drawable.ic_pause 
                else R.drawable.ic_play,
                if (musicPlayerManager.isPlaying.value == true) "Pause" else "Play",
                createActionIntent(ACTION_PLAY_PAUSE)
            )
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        startForeground(notificationId, notification)
    }

    private fun updateNotificationPlayback(isPlaying: Boolean) {
        val currentSong = musicPlayerManager.currentSong.value ?: return

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
            .setLargeIcon(loadAlbumArt(currentSong.coverUrl))
            .setContentTitle(currentSong.title)
            .setContentText(currentSong.artist)
            .setContentIntent(createContentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                createActionIntent(ACTION_PLAY_PAUSE)
            )
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(isPlaying)
            .build()

        notificationManager?.notify(notificationId, notification)
    }

    private fun updateNotificationProgress() {
        lifecycleScope.launch {
            while (true) {
                if (musicPlayerManager.isPlaying.value == true) {
                    val currentSong = musicPlayerManager.currentSong.value ?: continue
                    val currentPosition = musicPlayerManager.getCurrentPosition()
                    val duration = musicPlayerManager.getDuration()
                    val progress = if (duration > 0) (currentPosition * 100 / duration) else 0

                    val notification = NotificationCompat.Builder(this@NotificationService, channelId)
                        .setSmallIcon(R.mipmap.ic_purrytify_logo_foreground)
                        .setLargeIcon(loadAlbumArt(currentSong.coverUrl))
                        .setContentTitle(currentSong.title)
                        .setContentText(currentSong.artist)
                        .setContentIntent(createContentIntent())
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
                        .addAction(
                            if (musicPlayerManager.isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play,
                            if (musicPlayerManager.isPlaying.value == true) "Pause" else "Play",
                            createActionIntent(ACTION_PLAY_PAUSE)
                        )
                        .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
                        .addAction(R.drawable.ic_stop, "Stop", createActionIntent(ACTION_STOP))
                        .setStyle(
                            androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.sessionToken)
                                .setShowActionsInCompactView(0, 1, 2)
                        )
                        .setProgress(100, progress, false)
                        .setSubText(formatTime(currentPosition) + " / " + formatTime(duration))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setOngoing(true)
                        .build()

                    notificationManager?.notify(notificationId, notification)
                }
                delay(1000)
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

    private fun formatTime(millis: Int): String {
        val minutes = millis / 1000 / 60
        val seconds = millis / 1000 % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    private var progressUpdateJob: Job? = null

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            while (true) {
                musicPlayerManager.currentSong.value?.let { song ->
                    if (musicPlayerManager.isPlaying.value == true) {
                        updatePlayerNotification(song, true)
                    }
                }
                delay(1000) // Update every second
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
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or 
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
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
            })
        }
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
            ACTION_PLAY_PAUSE -> musicPlayerManager.togglePlayPause()
            ACTION_NEXT -> musicPlayerManager.playNextSong()
            ACTION_PREVIOUS -> musicPlayerManager.playPreviousSong()
            ACTION_STOP -> {
                musicPlayerManager.release()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observePlayback() {
        musicPlayerManager.currentSong.observe(this) { song ->
            song?.let { updatePlayerNotification(it,false) }
        }

        musicPlayerManager.isPlaying.observe(this) { isPlaying ->
            musicPlayerManager.currentSong.value?.let {
                updatePlayerNotification(it, isPlaying)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
    }
}