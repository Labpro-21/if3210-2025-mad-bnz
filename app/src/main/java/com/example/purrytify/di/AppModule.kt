package com.example.purrytify.di

import android.content.Context
import androidx.room.Room
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.network.ApiClient
import com.example.purrytify.network.ApiService
import com.example.purrytify.player.MusicPlayer
import com.example.purrytify.room.AppDatabase
import com.example.purrytify.room.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    @Singleton
    fun provideApiService(): ApiService = ApiClient.instance

    @Provides
    @Singleton
    fun provideMusicPlayer(@ApplicationContext context: Context): MusicPlayer = MusicPlayer(context)

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)
}