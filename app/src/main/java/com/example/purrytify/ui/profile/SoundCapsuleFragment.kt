package com.example.purrytify.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.purrytify.model.SoundCapsule
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SoundCapsuleFragment : Fragment() {
    private var _binding: FragmentSoundCapsuleBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SoundCapsuleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSoundCapsuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        setupExport()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            btnExport.setOnClickListener {
                viewModel.exportAnalytics()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.soundCapsule.observe(viewLifecycleOwner) { capsule ->
            if (capsule != null) {
                updateUI(capsule)
            } else {
                showNoData()
            }
        }
    }

    private fun updateUI(capsule: SoundCapsule) {
        binding.apply {
            // Time listened
            tvTimeListened.text = "${capsule.timeListened}"
            tvTimeListenedLabel.text = "minutes this month"
            tvDailyAverage.text = "Daily average: ${capsule.timeListened / 30} min"

            // Top artists
            rvTopArtists.apply {
                adapter = TopArtistsAdapter(capsule.topArtists)
                layoutManager = LinearLayoutManager(context)
            }

            // Top songs
            rvTopSongs.apply {
                adapter = TopSongsAdapter(capsule.topSongs)
                layoutManager = LinearLayoutManager(context)
            }

            // Streaks
            if (capsule.streaks.isNotEmpty()) {
                val streak = capsule.streaks.first()
                tvStreakTitle.text = "${streak.songTitle} by ${streak.artist}"
                tvStreakDays.text = "${streak.daysCount}-day streak"
                tvStreakDates.text = "${formatDate(streak.startDate)} - ${formatDate(streak.endDate)}"
            }
        }
    }

    private fun showNoData() {
        binding.apply {
            groupData.visibility = View.GONE
            tvNoData.visibility = View.VISIBLE
        }
    }
}