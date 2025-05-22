package com.example.purrytify.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.network.ApiClient
import com.example.purrytify.network.ApiService
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.room.AppDatabase
import com.example.purrytify.room.SongDao
import com.example.purrytify.repository.OnlineSongRepository
import com.example.purrytify.repository.SongRepository
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
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()
    }

    @Provides
    fun provideContext(application: Application): Context {
        return application.applicationContext
    }

    @Provides
    @Singleton
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    @Singleton
    fun provideApiService(): ApiService = ApiClient.instance

    @Provides
    @Singleton
    fun provideMusicPlayerManager(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        onlineSongRepository: OnlineSongRepository
    ): MusicPlayerManager {
        return MusicPlayerManager(context, songRepository, onlineSongRepository)
    }

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideOnlineSongRepository(
        apiService: ApiService,
        @ApplicationContext context: Context,
        songRepository: SongRepository
    ): OnlineSongRepository {
        return OnlineSongRepository(apiService, context, songRepository)
    }
}