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