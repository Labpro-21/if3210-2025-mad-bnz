package com.example.purrytify.ui.common

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ViewFlipper
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.MiniPlayerBinding
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

abstract class BaseFragment : Fragment() {
    private val TAG = "BaseFragment"

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    private var miniPlayer: MiniPlayerBinding? = null

    private var viewFlipper: ViewFlipper? = null
    private var isNetworkErrorShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Starting base fragment setup")
        view.post {
            Log.d(TAG, "onViewCreated: Post-layout setup of mini player")
            setupMiniPlayer(view)
        }
        viewFlipper = view.findViewById(R.id.viewFlipper)
        NetworkUtils.observeNetworkStatus(requireContext()) { isConnected ->
            if (isConnected) {
                hideNetworkError()
                if (isNetworkErrorShown) {
                    onReconnected()
                    isNetworkErrorShown = false
                }
            } else {
                showNetworkError()
                isNetworkErrorShown = true
            }
        }
    }
    protected fun checkNetworkBeforeLoading(onSuccess: () -> Unit) {
        if (NetworkUtils.isNetworkAvailable(requireContext())) {
            onSuccess()
        } else {
            showNetworkError()
        }
    }
    private fun showNetworkError() {
        viewFlipper?.apply {
            if (childCount > 1 && displayedChild != 1) {
                displayedChild = 1 // Show network error layout
            }
        }
    }
    private fun hideNetworkError() {
        viewFlipper?.apply {
            if (childCount > 0 && displayedChild != 0) {
                displayedChild = 0 // Show content layout
            }
        }
    }
    open fun onReconnected() {
        // Default implementation does nothing
    }

    private fun setupMiniPlayer(rootView: View) {
        try {
            Log.d(TAG, "setupMiniPlayer: Starting mini player setup")
            val miniPlayerView = rootView.findViewById<View>(R.id.miniPlayerLayout)
            Log.d(TAG, "setupMiniPlayer: MiniPlayerView found: ${miniPlayerView != null}")
            
            if (miniPlayerView != null) {
                miniPlayer = MiniPlayerBinding.bind(miniPlayerView)
                Log.d(TAG, "setupMiniPlayer: Mini player binding successful")
                
                // First, set initial visibility to GONE
                miniPlayer?.miniPlayerLayout?.visibility = View.GONE
                Log.d(TAG, "setupMiniPlayer: Set initial visibility to GONE")

                musicPlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
                    Log.d(TAG, "setupMiniPlayer: Current song changed: ${song?.title}")
                    if (song != null) {
                        lifecycleScope.launch {
                            miniPlayer?.apply {
                                Log.d(TAG, "setupMiniPlayer: Updating mini player UI")
                                
                                // Check player fragment visibility before showing
                                val isPlayerVisible = musicPlayerManager.isPlayerFragmentVisible.value
                                Log.d(TAG, "setupMiniPlayer: Player fragment visible: $isPlayerVisible")
                                
                                miniPlayerLayout.visibility = if (isPlayerVisible) {
                                    Log.d(TAG, "setupMiniPlayer: Hiding mini player - player fragment visible")
                                    View.GONE
                                } else {
                                    Log.d(TAG, "setupMiniPlayer: Showing mini player - has song and player not visible")
                                    View.VISIBLE
                                }
                                
                                miniPlayerTitle.text = song.title
                                miniPlayerArtist.text = song.artist
                                withContext(Dispatchers.IO) {
                                    val bitmap = try {

                                        null
                                    } catch (e: Exception) {
                                        null
                                    }
                                    withContext(Dispatchers.Main) {
                                    }
                                }
                                root.setOnClickListener {
                                    // Add pre-animation setup
                                    it.animate()
                                        .setDuration(150)
                                        .scaleX(0.95f)
                                        .scaleY(0.95f)
                                        .withEndAction {
                                            it.animate()
                                                .setDuration(100)
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .withEndAction {
                                                    // Navigate with animation
                                                    findNavController().navigate(R.id.action_global_playerFragment)
                                                }
                                        }
                                        .start()
                                }

                                minibtnPlayPause.setOnClickListener {
                                    musicPlayerManager.togglePlayPause()
                                }
                            }
                        }

                    } else {
                        Log.d(TAG, "setupMiniPlayer: No song, hiding mini player")
                        miniPlayer?.miniPlayerLayout?.visibility = View.GONE
                    }
                }

                // Add visibility state observer
                lifecycleScope.launch {
                    musicPlayerManager.isPlayerFragmentVisible.collect { isVisible ->
                        Log.d(TAG, "setupMiniPlayer: Player visibility changed: $isVisible")
                        miniPlayer?.miniPlayerLayout?.visibility = when {
                            isVisible -> {
                                Log.d(TAG, "setupMiniPlayer: Hiding mini player - player visible")
                                View.GONE
                            }
                            musicPlayerManager.currentSong.value != null -> {
                                Log.d(TAG, "setupMiniPlayer: Showing mini player - has song")
                                View.VISIBLE
                            }
                            else -> {
                                Log.d(TAG, "setupMiniPlayer: Hiding mini player - no song")
                                View.GONE
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupMiniPlayer: Error setting up mini player", e)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        miniPlayer?.minibtnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause
            else R.drawable.ic_play
        )
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up mini player")
        miniPlayer = null
        super.onDestroyView()
    }
}