package com.example.purrytify.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.auth.LoginActivity
import com.example.purrytify.auth.TokenRefreshService
import com.example.purrytify.databinding.ActivityMainBinding
import com.example.purrytify.model.Song
import com.example.purrytify.network.NetworkStatus
import com.example.purrytify.network.NetworkStatusHelper
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.player.PlayerViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager
    private lateinit var logoutReceiver: BroadcastReceiver
    private lateinit var networkStatusHelper: NetworkStatusHelper
    private lateinit var snackbar: Snackbar
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupBottomNavigation()

            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    musicPlayerManager.initialize()
                }
            }
            val miniPlayer = findViewById<ConstraintLayout>(R.id.miniPlayerLayout)
            val playPauseButton = findViewById<ImageView>(R.id.minibtnPlayPause)
            val nextButton = findViewById<ImageView>(R.id.minibtnNext)
            val miniPlayerTitle = findViewById<TextView>(R.id.miniPlayerTitle)
            val miniPlayerArtist = findViewById<TextView>(R.id.miniPlayerArtist)

            if (miniPlayer != null && playPauseButton != null) {
                musicPlayerManager.currentSong.observe(this) { song ->
                    if (song != null) {
                        miniPlayer.visibility = View.VISIBLE
                        miniPlayerTitle?.text = song.title
                        miniPlayerArtist?.text = song.artist
                        updateMiniPlayerUI(song = song)
                        Log.d("MainActivity", "Updated mini player: ${song.title} (ID: ${song.id})")
                    } else {
                        miniPlayer.visibility = View.GONE
                    }
                }
                musicPlayerManager.isPlaying.observe(this) { isPlaying ->
                    playPauseButton?.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }

                miniPlayer.setOnClickListener {
                    findNavController(R.id.nav_host_fragment).navigate(R.id.playerFragment)
                }

                playPauseButton.setOnClickListener {
                    musicPlayerManager.togglePlayPause()
                }
                nextButton.setOnClickListener{
                    musicPlayerManager.playNextSong()
                }
            } else {
                Log.e("MainActivity", "Mini player views not found in layout")
            }

            logoutReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == TokenRefreshService.ACTION_LOGOUT) {
                        logoutUser()
                    }
                }
            }

            registerReceiver(
                logoutReceiver,
                IntentFilter(TokenRefreshService.ACTION_LOGOUT),
                Context.RECEIVER_EXPORTED // Add this flag
            )
            setupNetworkMonitoring()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }


    }
    private fun updateMiniPlayerUI(song: Song) {
        try {
            val songTitle = findViewById<TextView>(R.id.miniPlayerTitle)
            val artistName = findViewById<TextView>(R.id.miniPlayerArtist)
            val coverImage = findViewById<ImageView>(R.id.miniPlayerCover)
            val progressBar = findViewById<ProgressBar>(R.id.miniPlayerProgress)

            songTitle.text = song.title
            artistName.text = song.artist
            Glide.with(this)
                .load(song.coverUrl)
                .placeholder(R.drawable.ic_placeholder_album)
                .transform(RoundedCorners(10))
                .override(48, 48)
                .into(coverImage)

            lifecycleScope.launch {
                while (true) {
                    try {
                        if (musicPlayerManager.isPlaying.value == true) {
                            val position = musicPlayerManager.getCurrentPosition()
                            val duration = musicPlayerManager.getDuration()
                            if (duration > 0) {
                                progressBar.progress = (position * 100 / duration)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error updating progress", e)
                    }
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating mini player UI", e)
        }
    }
    private fun logoutUser() {
        viewModel.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    private fun setupNetworkMonitoring() {
        networkStatusHelper = NetworkStatusHelper(this)

        networkStatusHelper.networkStatus.observe(this) { status ->
            when (status) {
                is NetworkStatus.Available -> hideNetworkError()
                is NetworkStatus.Unavailable -> showNetworkError()
                is NetworkStatus.Error -> showNetworkError()
            }
        }
        networkStatusHelper.register()
    }

    private fun showNetworkError() {
        snackbar = Snackbar.make(
            binding.root,
            "No internet connection",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setBackgroundTint(getColor(R.color.colorError))
            setTextColor(Color.WHITE)
            show()
        }
    }

    private fun hideNetworkError() {
        if (::snackbar.isInitialized && snackbar.isShown) {
            snackbar.dismiss()
        }
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }
    override fun onDestroy() {
        super.onDestroy()
//        lifecycleScope.coroutineContext.cancelChildren()
        musicPlayerManager.cleanup()
        unregisterReceiver(logoutReceiver)
        networkStatusHelper.unregister()
    }
}