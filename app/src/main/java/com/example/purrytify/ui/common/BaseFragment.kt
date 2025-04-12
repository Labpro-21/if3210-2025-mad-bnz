package com.example.purrytify.ui.common

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.MiniPlayerBinding
import com.example.purrytify.player.MusicPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

abstract class BaseFragment : Fragment() {

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    private var miniPlayer: MiniPlayerBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            setupMiniPlayer(view)
        }
    }

    private fun setupMiniPlayer(rootView: View) {
        try {
            val miniPlayerView = rootView.findViewById<View>(R.id.miniPlayerLayout)
            if (miniPlayerView != null) {
                miniPlayer = MiniPlayerBinding.bind(miniPlayerView)

                // Observe current playing song
                musicPlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
                    if (song != null) {
                        // Update visibility and content in a UI-safe way
                        lifecycleScope.launch {
                            miniPlayer?.apply {
                                miniPlayerLayout.visibility = View.VISIBLE
                                miniPlayerTitle.text = song.title
                                miniPlayerArtist.text = song.artist

                                // Load album cover in background thread
                                withContext(Dispatchers.IO) {
                                    val bitmap = try {
                                        // Load bitmap in background
                                        null  // Replace with actual loading
                                    } catch (e: Exception) {
                                        null
                                    }

                                    // Update UI on main thread
                                    withContext(Dispatchers.Main) {
                                        // Use Glide or set bitmap directly
                                    }
                                }

                                // Set click listeners
                                root.setOnClickListener {
                                    // Navigate to player using direct resource ID
                                    findNavController().navigate(R.id.playerFragment)
                                }

                                minibtnPlayPause.setOnClickListener {
                                    musicPlayerManager.togglePlayPause()
                                }
                            }
                        }
                    } else {
                        miniPlayer?.miniPlayerLayout?.visibility = View.GONE
                    }
                }

                // Observe play state changes
                musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
                    updatePlayPauseButton(isPlaying)
                }
            }
        } catch (e: Exception) {
            // Log exception but don't crash the app
            e.printStackTrace()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        miniPlayer?.minibtnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause
            else R.drawable.ic_play
        )
    }

    override fun onDestroyView() {
        miniPlayer = null
        super.onDestroyView()
    }
}