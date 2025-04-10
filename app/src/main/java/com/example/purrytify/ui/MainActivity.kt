package com.example.purrytify.ui
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityMainBinding
import com.example.purrytify.player.MusicPlayer
import com.example.purrytify.player.PlayerViewModel
import com.example.purrytify.utils.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var musicPlayer: MusicPlayer
    @Inject lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        observeNetworkStatus()
        setupMiniPlayer()
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment)
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun observeNetworkStatus() {
        NetworkUtils.observeNetworkStatus(this) { isConnected ->
            if (!isConnected) {
                showToast("No internet connection")
            }
        }
    }

    private fun setupMiniPlayer() {
        playerViewModel.currentSong.observe(this) { song ->
            song?.let {
                binding.miniPlayer.root.visibility = View.VISIBLE
                binding.miniPlayer.tvTitle.text = it.title
                binding.miniPlayer.tvArtist.text = it.artist

                binding.miniPlayer.root.setOnClickListener {
                    startActivity(Intent(this, PlayerActivity::class.java))
                }
            } ?: run {
                binding.miniPlayer.root.visibility = View.GONE
            }
        }
    }
}