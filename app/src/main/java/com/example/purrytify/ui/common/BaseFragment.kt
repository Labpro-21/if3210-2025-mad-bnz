package com.example.purrytify.ui.common

import android.os.Bundle
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

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    private var miniPlayer: MiniPlayerBinding? = null

    private var viewFlipper: ViewFlipper? = null
    private var isNetworkErrorShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
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
            val miniPlayerView = rootView.findViewById<View>(R.id.miniPlayerLayout)
            if (miniPlayerView != null) {
                miniPlayer = MiniPlayerBinding.bind(miniPlayerView)
                musicPlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
                    if (song != null) {
                        lifecycleScope.launch {
                            miniPlayer?.apply {
                                miniPlayerLayout.visibility = View.VISIBLE
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
                        miniPlayer?.miniPlayerLayout?.visibility = View.GONE
                    }
                }
                musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
                    updatePlayPauseButton(isPlaying)
                }
            }
        } catch (e: Exception) {
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