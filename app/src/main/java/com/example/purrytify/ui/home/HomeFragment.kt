package com.example.purrytify.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.model.Song
import com.example.purrytify.ui.HomeViewModel
import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.ui.library.SongAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var newReleasesAdapter: SongAdapter
    private lateinit var recentlyPlayedAdapter: SongHorizontalAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        observeViewModel()
    }

    private fun setupAdapters() {
        newReleasesAdapter = SongAdapter(
            onItemClick = { song ->
                navigateToPlayer(song)
            },
            onLikeClick = { song ->
                viewModel.toggleLike(song)
            },
            onPlayClick = { song ->
                playSong(song)
            },
            musicPlayerManager = musicPlayerManager
        )
        musicPlayerManager.currentSong.observe(viewLifecycleOwner) { _ ->
            newReleasesAdapter.notifyDataSetChanged()
        }

        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { _ ->
            newReleasesAdapter.notifyDataSetChanged()
        }

        binding.rvNewSongs.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = newReleasesAdapter
        }
        binding.tvUsername.setText(
            "Hi Assistant!"
        )
        binding.ivProfile.setImageResource(R.drawable.ic_person)


        recentlyPlayedAdapter = SongHorizontalAdapter(
            onItemClick = { song ->
                navigateToPlayer(song)
            },
            onPlayClick = { song ->
                playSong(song)
            },
            musicPlayerManager= musicPlayerManager
        )

        musicPlayerManager.currentSong.observe(viewLifecycleOwner) { _ ->
            recentlyPlayedAdapter.notifyDataSetChanged()
        }

        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { _ ->
            recentlyPlayedAdapter.notifyDataSetChanged()
        }

            binding.rvRecentlyPlayed.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = recentlyPlayedAdapter
            }
    }

    private fun navigateToPlayer(song: Song) {
        findNavController().navigate(
            R.id.action_homeFragment_to_playerFragment
        )
    }

    private fun playSong(song: Song) {
        musicPlayerManager.playSong(song)
        viewModel.playSong(song)
    }

    private fun observeViewModel() {
        viewModel.newReleaseSongs.observe(viewLifecycleOwner) { songs ->
            newReleasesAdapter.submitList(songs)
        }
        viewModel.recentlyPlayedSongs.observe(viewLifecycleOwner) { songs ->
            recentlyPlayedAdapter.submitList(songs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}