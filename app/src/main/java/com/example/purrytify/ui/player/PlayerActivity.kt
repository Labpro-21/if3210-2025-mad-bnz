package com.example.purrytify.ui.player

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.purrytify.R
import com.example.purrytify.databinding.ActivityPlayerBinding
import com.example.purrytify.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        binding.btnNext.setOnClickListener { /* Handle next */ }
        binding.btnPrevious.setOnClickListener { /* Handle previous */ }
        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
    }

    private fun observeViewModel() {
        viewModel.currentSong.observe(this) { song ->
            song?.let {
                binding.tvTitle.text = it.title
                binding.tvArtist.text = it.artist
                binding.btnLike.setImageResource(
                    if (it.isLiked) R.drawable.ic_liked else R.drawable.ic_like
                )
            }
        }

        viewModel.isPlaying.observe(this) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }
}