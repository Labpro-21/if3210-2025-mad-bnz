package com.example.purrytify.ui.home


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentHomeBinding
import com.example.purrytify.model.Song
import com.example.purrytify.ui.HomeViewModel
import com.example.purrytify.ui.charts.ChartsFragment
import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.ui.library.SongAdapter
import com.example.purrytify.utils.Constants
import com.example.purrytify.utils.CountryUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var newReleasesAdapter: SongGridAdapter
    private lateinit var recentlyPlayedAdapter: SongAdapter
    private lateinit var recommendedAdapter: PlaylistAdapter

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

        binding.cardGlobalChart.setOnClickListener {
            val bundle = Bundle().apply {
                putString("chartType", "global")
            }
            findNavController().navigate(
                R.id.action_homeFragment_to_chartsFragment,
                bundle
            )
        }

        // For country charts
        val userCountry = viewModel.userInfo.value?.location ?: "ID"
        binding.cardCountryChart.setOnClickListener {
            val bundle = Bundle().apply {
                putString("chartType",userCountry )
            }
            findNavController().navigate(
                R.id.action_homeFragment_to_chartsFragment,
                bundle
            )
        }

        setupToolbar()
        setupAdapters()
        setupRecyclerViews()
        observeViewModel()
    }

    private fun updateNewSongsSection(songs: List<Song>) {
        binding.apply {
            if (songs.isEmpty()) {
                rvNewSongs.visibility = View.GONE
                layoutNewSongsEmpty.root.visibility = View.VISIBLE
            } else {
                rvNewSongs.visibility = View.VISIBLE
                layoutNewSongsEmpty.root.visibility = View.GONE
                newReleasesAdapter.submitList(songs)
            }
        }
    }

    private fun updateRecentlyPlayedSection(songs: List<Song>) {
        binding.apply {
            if (songs.isEmpty()) {
                rvRecentlyPlayed.visibility = View.GONE
                layoutRecentlyPlayedEmpty.root.visibility = View.VISIBLE
            } else {
                rvRecentlyPlayed.visibility = View.VISIBLE
                layoutRecentlyPlayedEmpty.root.visibility = View.GONE
                recentlyPlayedAdapter.submitList(songs)
            }
        }
    }

    private fun setupToolbar() {
        binding.ivProfile.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
        }

        // Load user profile image if available
        viewModel.userInfo.observe(viewLifecycleOwner) { user ->
            Glide.with(requireContext())
                .load("${Constants.BASE_URL}uploads/profile-picture/${user?.profilePhoto}")
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .circleCrop()
                .into(binding.ivProfile)
        }
    }


    private fun setupAdapters() {

        recommendedAdapter = PlaylistAdapter { playlist ->
            musicPlayerManager.playRecommendedPlaylist(playlist)
            navigateToPlayer(playlist.songs.first())
        }

        binding.rvRecommended.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recommendedAdapter
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.bottom_nav_height))
            clipToPadding = false
        }


        newReleasesAdapter = SongGridAdapter(
            onItemClick ={ song -> navigateToPlayer(song)} ,
            onItemPlay = { song ->
                musicPlayerManager.playSong(song)
            },
            musicPlayerManager = musicPlayerManager
        )
        binding.tvCountryName.text = CountryUtils.getCountryName(viewModel.userInfo.value?.location.toString()) ?: "Loading..."


        recentlyPlayedAdapter = SongAdapter(
            onItemPlay = {song -> musicPlayerManager.playSong(song)},
            onNavigateToPlayer = { song -> navigateToPlayer(song) },
            onLikeClick = { song -> viewModel.toggleLike(song) },
            musicPlayerManager = musicPlayerManager
        )

        binding.rvNewSongs.apply {

            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = newReleasesAdapter

        }

        binding.rvRecentlyPlayed.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = recentlyPlayedAdapter
        }


        binding.ivProfile.setImageResource(R.drawable.ic_person)


        musicPlayerManager.apply {
            currentSong.observe(viewLifecycleOwner) {
                newReleasesAdapter.notifyDataSetChanged()
                recentlyPlayedAdapter.notifyDataSetChanged()
            }

            isPlaying.observe(viewLifecycleOwner) {
                newReleasesAdapter.notifyDataSetChanged()
                recentlyPlayedAdapter.notifyDataSetChanged()
            }
        }
    }


    private fun setupRecyclerViews() {
        binding.rvRecentlyPlayed.apply {
            val divider = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
            ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                divider.setDrawable(it)
            }
            addItemDecoration(divider)
        }
    }

    private fun navigateToPlayer(song: Song) {
        findNavController().navigate(
            R.id.action_homeFragment_to_playerFragment
        )
    }


    private fun observeViewModel() {
        viewModel.newReleaseSongs.observe(viewLifecycleOwner) { songs ->
//            newReleasesAdapter.submitList(songs)
            updateNewSongsSection(songs)
        }
        viewModel.recentlyPlayedSongs.observe(viewLifecycleOwner) { songs ->
//            recentlyPlayedAdapter.submitList(songs)
            updateRecentlyPlayedSection(songs)
        }
        viewModel.recommendedPlaylists.observe(viewLifecycleOwner) { playlists ->
            recommendedAdapter.submitList(playlists)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}