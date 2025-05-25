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
import android.widget.Toast
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
import com.example.purrytify.databinding.MiniPlayerBinding
import com.example.purrytify.model.Song
import com.example.purrytify.network.NetworkStatus
import com.example.purrytify.network.NetworkStatusHelper
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.player.PlayerViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var miniPlayerBinding: MiniPlayerBinding? = null
    @Inject 
    lateinit var musicPlayerManager: MusicPlayerManager
    private lateinit var networkStatusHelper: NetworkStatusHelper
    private lateinit var snackbar: Snackbar
    private val viewModel: MainViewModel by viewModels()
    private var updateJob: Job? = null

    private lateinit var logoutReceiver: BroadcastReceiver

    private var isAppInitialized = false
    private var pendingDeepLinkIntent: Intent? = null

    private val TAG = "MainActivity" // Add this for consistent logging

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting initialization")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loadingView.visibility = View.VISIBLE

        Log.d(TAG, "onCreate: Setting initial mini player visibility to GONE")
        binding.miniPlayerLayout.root.visibility = View.GONE
        
        try {
            Log.d(TAG, "onCreate: Starting setup sequence")
            setupInitialUI()
            lifecycleScope.launch(Dispatchers.Main) {
                initializeComponents()
                isAppInitialized = true
                binding.loadingView.visibility = View.GONE

                pendingDeepLinkIntent?.let {
                    handleDeepLinkIfNeeded(it)
                    pendingDeepLinkIntent = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error during initialization", e)
            handleInitializationError(e)
        }
        if (intent?.data != null) {
            pendingDeepLinkIntent = intent
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!isAppInitialized) {
            pendingDeepLinkIntent = intent
        } else {
            handleDeepLinkIfNeeded(intent)
        }
    }

    private fun handleDeepLinkIfNeeded(intent: Intent?) {
        if (!isAppInitialized) {
            Log.d(TAG, "handleDeepLinkIfNeeded: App not initialized, pending deep link")
            pendingDeepLinkIntent = intent
            return
        }
        intent?.data?.let { uri ->
            if (uri.scheme == "purrytify" && uri.host == "song") {
                val songId = uri.lastPathSegment
                Log.d(TAG, "handleDeepLinkIfNeeded: Deep link to songId=$songId")
                if (songId != null){
                    fetchAndPlaySongById(songId)
                }
            }
        }
    }
    private fun fetchAndPlaySongById(songId: String) {
        val playerViewModel: PlayerViewModel by viewModels()
        lifecycleScope.launch {
            try {
                playerViewModel.fetchAndPlayOnlineSong(songId) { song ->
                    if (song != null) {
                        Log.d(TAG, "fetchAndPlaySongById: Song fetched, playing song: $song")
                        musicPlayerManager.playSong(song)

                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndPlaySongById: Failed to load song", e)
            }
        }
    }

    private fun setupInitialUI() {
        Log.d(TAG, "setupInitialUI: Starting UI setup")
        binding.apply {
            Log.d(TAG, "setupInitialUI: Setting up bottom navigation")
            setupBottomNavigation()
            Log.d(TAG, "setupInitialUI: Setting mini player visibility to GONE")
            miniPlayerLayout.root.visibility = View.GONE
        }
    }

    private suspend fun initializeComponents() {
        Log.d(TAG, "initializeComponents: Starting components initialization")
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "initializeComponents: Initializing MusicPlayerManager")
                withContext(Dispatchers.Default) {
                    musicPlayerManager.initialize()
                }

                Log.d(TAG, "initializeComponents: Setting up mini player")
                setupMiniPlayer()

                Log.d(TAG, "initializeComponents: Setting up network monitoring")
                setupNetworkMonitoring()

                Log.d(TAG, "initializeComponents: Registering broadcast receivers")
                registerBroadcastReceivers()

                Log.d(TAG, "initializeComponents: Setting up player visibility observer")
                observePlayerVisibility()
            } catch (e: Exception) {
                Log.e(TAG, "initializeComponents: Error during component initialization", e)
                handleInitializationError(e)
            }
        }
    }

    private fun observePlayerVisibility() {
        lifecycleScope.launch {
            musicPlayerManager.isPlayerFragmentVisible.collect { isVisible ->

                binding.miniPlayerLayout?.root?.visibility = when {
                    isVisible -> {

                        View.GONE
                    }
                    musicPlayerManager.currentSong.value != null -> {

                        View.VISIBLE
                    }
                    else -> {
                        View.GONE
                    }
                }
            }
        }
    }

    private fun registerBroadcastReceivers() {
        logoutReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TokenRefreshService.ACTION_LOGOUT) {
                    logoutUser()
                }
            }
        }
        val intentFilter = IntentFilter(TokenRefreshService.ACTION_LOGOUT)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logoutReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logoutReceiver, intentFilter)
        }
    }

    private fun handleInitializationError(error: Exception) {
        Toast.makeText(
            this,
            "Failed to initialize app. Please try again.",
            Toast.LENGTH_LONG
        ).show()

        Log.e("MainActivity", "Initialization error", error)
        finish()
    }

    private fun setupMiniPlayer() {
        Log.d(TAG, "setupMiniPlayer: Starting mini player setup")
        binding.miniPlayerLayout?.apply {
            Log.d(TAG, "setupMiniPlayer: Setting initial visibility to GONE")
            root.visibility = View.GONE
            
            lifecycleScope.launch {
                Log.d(TAG, "setupMiniPlayer: Setting up visibility collectors and observers")
                launch {
                    musicPlayerManager.isPlayerFragmentVisible.collect { isPlayerVisible ->
                        Log.d(TAG, "setupMiniPlayer: Player fragment visibility changed to: $isPlayerVisible")
                        if (isPlayerVisible) {
                            root.visibility = View.GONE
                        } else {
                            root.visibility = if (musicPlayerManager.currentSong.value != null) {
                                Log.d(TAG, "setupMiniPlayer: Showing mini player - song exists")
                                View.VISIBLE
                            } else {
                                Log.d(TAG, "setupMiniPlayer: Hiding mini player - no song")
                                View.GONE
                            }
                        }
                    }
                }
                musicPlayerManager.currentSong.observe(this@MainActivity) { song ->
                    if (song != null) {
                        root.visibility = View.VISIBLE
                        miniPlayerTitle.text = song.title
                        miniPlayerArtist.text = song.artist
                        updateMiniPlayerUI(song)
                    } else {
                        root.visibility = View.GONE
                    }
                }
                musicPlayerManager.isPlaying.observe(this@MainActivity) { isPlaying ->
                    minibtnPlayPause?.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }

            }

            // Setup click listeners
            root.setOnClickListener {
                findNavController(R.id.nav_host_fragment).navigate(R.id.playerFragment)
            }

            minibtnPlayPause.setOnClickListener {
                musicPlayerManager.togglePlayPause()
            }

            minibtnNext.setOnClickListener {
                musicPlayerManager.playNextSong()
            }
        }
    }

    private fun updateMiniPlayerUI(song: Song) {
        binding.apply {
            miniPlayerLayout?.let { layout ->
                layout.miniPlayerTitle.text = song.title
                layout.miniPlayerArtist.text = song.artist

                Glide.with(this@MainActivity)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.ic_placeholder_album)
                    .transform(RoundedCorners(8))
                    .override(48, 48)
                    .into(layout.miniPlayerCover)

                updateJob?.cancel()
                updateJob = lifecycleScope.launch {
                    while (isActive) {
                        try {
                            if (musicPlayerManager.isPlaying.value == true) {
                                val position = musicPlayerManager.getCurrentPosition()
                                val duration = musicPlayerManager.getDuration()
                                if (duration > 0) {
                                    layout.miniPlayerProgress.progress = (position * 100 / duration)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error updating progress", e)
                        }
                        delay(1000)
                    }
                }
            }
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
        Log.d(TAG, "onDestroy: Starting cleanup")
        try {
            updateJob?.cancel()
            musicPlayerManager.cleanup()
            if (::logoutReceiver.isInitialized) {
                unregisterReceiver(logoutReceiver)
            }
            if (::networkStatusHelper.isInitialized) {
                networkStatusHelper.unregister()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error during cleanup", e)
        } finally {
            super.onDestroy()
        }
    }
}