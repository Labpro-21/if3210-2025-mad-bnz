package com.example.purrytify.network

import com.example.purrytify.model.SongResponse
import com.example.purrytify.model.UpdatePhotoResponse
import com.example.purrytify.model.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @GET("api/top-songs/global")
    suspend fun getGlobalTopSongs(): Response<List<SongResponse>>
    
    @GET("api/top-songs/{country_code}")
    suspend fun getCountryTopSongs(@Path("country_code") countryCode: String): Response<List<SongResponse>>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @POST("api/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<Unit>

    @PATCH("api/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body requestBody: RequestBody
    ): Response<Unit>

    @GET("api/songs/{id}")
    suspend fun getSongById(@Path("id") id: String): Response<SongResponse>

    @Multipart
    @POST("api/profile/photo")
    suspend fun updateProfilePhoto(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): Response<UpdatePhotoResponse>
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)