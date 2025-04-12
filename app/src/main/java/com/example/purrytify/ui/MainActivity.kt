package com.example.purrytify.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityMainBinding
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

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
            } else {
                Log.e("MainActivity", "Mini player views not found in layout")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }
    private fun updateMiniPlayerUI(song: Song) {
        val songTitle = findViewById<TextView>(R.id.miniPlayerTitle)
        val artistName = findViewById<TextView>(R.id.miniPlayerArtist)
        val coverImage = findViewById<ImageView>(R.id.miniPlayerCover)

        songTitle.text = song.title
        artistName.text = song.artist
        Glide.with(this)
            .load(song.coverUrl)
            .placeholder(R.drawable.ic_placeholder_album)
            .into(coverImage)
    }
    private fun updatePlayPauseButton(button: ImageView) {
        musicPlayerManager.isPlaying.observe(this) { isPlaying ->
            button.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }
}