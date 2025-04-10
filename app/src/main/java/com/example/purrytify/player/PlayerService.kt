package com.example.purrytify.player

import android.content.Context
import android.content.Intent
import android.os.IBinder

class PlayerService : Service() {
    private val binder = PlayerBinder(this)
    private lateinit var musicPlayer: MusicPlayer

    override fun onCreate() {
        super.onCreate()
        musicPlayer = MusicPlayer(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    fun getMusicPlayer(): MusicPlayer = musicPlayer

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, PlayerService::class.java)
            context.startService(intent)
        }
    }
}