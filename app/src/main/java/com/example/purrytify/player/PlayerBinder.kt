package com.example.purrytify.player
import android.os.Binder

class PlayerBinder(private val service: PlayerService) : Binder() {
    fun getService(): PlayerService = service
}