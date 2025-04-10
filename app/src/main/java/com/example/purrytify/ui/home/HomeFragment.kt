package com.example.purrytify.ui.home
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.ui.library.SongAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recentSongsAdapter: SongAdapter
    private lateinit var newSongsAdapter: SongAdapter

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
        setupRecyclerViews()
        observeViewModel()
    }

    private fun setupAdapters() {
        recentSongsAdapter = SongAdapter(
            onItemClick = { song ->
                viewModel.playSong(song)
            },
            onLikeClick = { song ->
                viewModel.toggleLike(song)
            }
        )

        newSongsAdapter = SongAdapter(
            onItemClick = { song ->
                viewModel.playSong(song)
            },
            onLikeClick = { song ->
                viewModel.toggleLike(song)
            }
        )
    }

    private fun setupRecyclerViews() {
        binding.rvRecentlyPlayed.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentSongsAdapter
        }

        binding.rvNewSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newSongsAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            recentSongsAdapter.submitList(songs)
        }

        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            newSongsAdapter.submitList(songs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}