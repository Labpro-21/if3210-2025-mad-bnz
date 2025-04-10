package com.example.purrytify.player
class PlayerBinder(private val service: PlayerService) : Binder() {
    fun getService(): PlayerService = service
}