package com.example.purrytify.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentPlayerBinding
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager
    private var isSeekBarTracking = false
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val songFromArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable("song", Song::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable("song") as? Song
        }
        if (songFromArgs != null) {
            updateSongUI(songFromArgs)
            if (musicPlayerManager.currentSong.value?.id != songFromArgs.id) {
                musicPlayerManager.playSong(songFromArgs)
            }
        } else {
            musicPlayerManager.currentSong.value?.let {
                updateSongUI(it)
            }
        }
        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
        binding.btnPlayPause.setOnClickListener {
            musicPlayerManager.togglePlayPause()
        }
        binding.btnLike.setOnClickListener{
            playerViewModel.toggleLike()
        }
        setupUI()
        observeViewModel()
        observePlayerState()
        setupSeekBar()
        startTimeUpdate()
    }

    private fun setupUI() {

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnPlayPause.setOnClickListener {
            musicPlayerManager.togglePlayPause()
        }
        binding.btnLike.setOnClickListener {
            musicPlayerManager.currentSong.value?.let { song ->
                playerViewModel.toggleLike(song)
            }
        }
        binding.btnNext.setOnClickListener {
            musicPlayerManager.playNextSong()
        }

        binding.btnPrevious.setOnClickListener {
            musicPlayerManager.playPreviousSong()
        }

        binding.btnShuffle.setOnClickListener {
            musicPlayerManager.toggleShuffle()
        }
        binding.btnRepeat.setOnClickListener {
            musicPlayerManager.toggleRepeat()
        }

        binding.btnLike.setOnClickListener {
            musicPlayerManager.currentSong.value?.let { currentSong ->
                playerViewModel.toggleLike(currentSong)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    musicPlayerManager.seekTo(it.progress)
                }
                isSeekBarTracking = false
            }
        })
    }

    private fun observeViewModel() {
        playerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.tvTitle.text = song.title
                binding.tvArtist.text = song.artist
                Glide.with(this)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .into(binding.ivAlbumArt)

                binding.btnLike.setImageResource(
                    if (song.isLiked) R.drawable.ic_liked
                    else R.drawable.ic_like
                )
                binding.seekBar.max = song.duration.toInt()
                binding.tvTotalTime.text = formatDuration(song.duration)
            }
        }
        playerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause
                else R.drawable.ic_play
            )
        }
        playerViewModel.likedSongs.observe(viewLifecycleOwner) { likedSongs ->
            musicPlayerManager.currentSong.value?.let { currentSong ->
                val isLiked = likedSongs.any { it.id == currentSong.id }
                updateLikeButtonState(isLiked)
            }
        }
        playerViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (!binding.seekBar.isPressed) {
                binding.seekBar.progress = position.toInt()
                binding.tvCurrentTime.text = formatDuration(position)
            }
        }
    }
    private fun updateLikeButtonState(isLiked: Boolean) {
        binding.btnLike.setImageResource(
            if (isLiked) R.drawable.ic_liked
            else R.drawable.ic_like
        )
    }

    private fun setupSeekBar() {
        lifecycleScope.launch {
            while (true) {
                if (playerViewModel.isPlaying.value == true && !binding.seekBar.isPressed) {
                    val currentPosition = playerViewModel.getCurrentPosition()
                    binding.seekBar.progress = currentPosition
                    binding.tvCurrentTime.text = formatDuration(currentPosition.toLong())
                }
                delay(1000)
            }
        }
    }

    private fun formatDuration(duration: Long): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }


    private fun observePlayerState() {
        musicPlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                updateSongUI(song)
            } else {
                findNavController().navigateUp()
            }
        }

        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause
                else R.drawable.ic_play
            )
        }

        musicPlayerManager.songDuration.observe(viewLifecycleOwner) { duration ->
            binding.tvTotalTime.text = formatTime(duration)
            binding.seekBar.max = duration
        }

        musicPlayerManager.isShuffleEnabled.observe(viewLifecycleOwner) { isShuffleEnabled ->
            binding.btnShuffle.setImageResource(
                if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
            )
        }

        musicPlayerManager.repeatMode.observe(viewLifecycleOwner) { repeatMode ->
            val iconRes = when (repeatMode) {
                MusicPlayerManager.RepeatMode.OFF -> R.drawable.ic_repeat
                MusicPlayerManager.RepeatMode.REPEAT_ALL -> R.drawable.ic_repeat_all
                MusicPlayerManager.RepeatMode.REPEAT_ONE -> R.drawable.ic_repeat_one
            }
            binding.btnRepeat.setImageResource(iconRes)
        }


    }

    private fun updateSongUI(song: Song) {
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist
        Glide.with(requireContext())
            .load(song.coverUrl.takeIf { !it.isNullOrEmpty() })
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .into(binding.ivAlbumArt)
        playerViewModel.isLiked(song.id).observe(viewLifecycleOwner) { isLiked ->
            updateLikeButtonState(isLiked)
        }
    }
    private fun startTimeUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (!isSeekBarTracking) {
                    val position = musicPlayerManager.getCurrentPosition()
                    binding.tvCurrentTime.text = formatTime(position)
                    binding.seekBar.progress = position
                }
                delay(1000)
            }
        }
    }

    private fun formatTime(millis: Int): String {
        val minutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}