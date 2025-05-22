package com.example.purrytify.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val profilePhoto: String,
    val location: String,
    val createdAt: String,
    val updatedAt: String
)
data class UpdatePhotoResponse(
    val success: Boolean,
    val message: String,
    val profilePhoto: String? = null
)