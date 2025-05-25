package com.example.purrytify

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.media3.common.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class PurrytifyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // Initialize app components
            if (BuildConfig.DEBUG) {
                enableStrictMode()
            }

            // Add crash reporting
            setupCrashHandling()
        } catch (e: Exception) {
            Log.e("PurrytifyApp", "Error initializing application", e)
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    private fun setupCrashHandling() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PurrytifyApp", "Uncaught exception in thread $thread", throwable)
        }
    }
}