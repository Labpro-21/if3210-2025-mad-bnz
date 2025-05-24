package com.example.purrytify.ui.charts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.R
import com.example.purrytify.auth.TokenManager
import com.example.purrytify.databinding.FragmentChartsBinding
import com.example.purrytify.model.DownloadStatus
import com.example.purrytify.utils.CountryUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartsFragment : Fragment() {
    private var _binding: FragmentChartsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChartsViewModel by viewModels()
    private val songAdapter = ChartSongAdapter(
        onPlayClick = { onlineSong -> 
            viewModel.playOnlineSong(onlineSong)
        },
        onDownloadClick = { onlineSong ->
            viewModel.downloadSong(onlineSong)
        }
    )
//    private val country = TokenManager.
    private var chartType: String = "global"

    companion object {
        const val ARG_CHART_TYPE = "chart_type"
        const val TYPE_GLOBAL = "global"
        const val TYPE_COUNTRY = "country"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chartType = arguments?.getString("chartType") ?: "global"
        setupUI(chartType)
        setupRecyclerView()
//        observeViewModel()

        setupObservers()
        viewModel.loadCharts(chartType)
    }
    private fun setupUI(chartType: String) {
        binding.apply {
            ivChartCover.setImageResource(R.drawable.ic_chart_cover)

            when (chartType) {
                "global" -> {
                    tvChartTitle.text = "Top 50"
                    tvChartSubtitle.text = "GLOBAL"
                }
                else -> {
                    tvChartTitle.text = "Top 10"
                    Log.e("Fragment CHart",chartType)
//                    if (!CountryUtils.isCountrySupported(chartType)) {
//                        throw IllegalArgumentException("Unsupported country code: $chartType")
//                    }
                    tvChartSubtitle.text = CountryUtils.getCountryName(chartType)
//                    tvChartSubtitle.text = (chartType)
                }
            }

            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvSongs.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            
            // Add divider
            val divider = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
            ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                divider.setDrawable(it)
            }
            addItemDecoration(divider)
        }
    }

    private fun setupObservers() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
        }

//        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
//            binding.loadingProgress.isVisible = isLoading
//        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.downloadProgress.collect { progressMap ->
                progressMap.forEach { (songId, progress) ->
                    songAdapter.updateDownloadProgress(songId, progress)
                }
            }
        }
//        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
//            binding.chartLoadingProgress.isVisible = isLoading
//        }

        viewModel.downloadStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is DownloadStatus.Success -> {
                    Toast.makeText(context, "Download completed", Toast.LENGTH_SHORT).show()
                }
                is DownloadStatus.Error -> {
                    Toast.makeText(context, "Download failed: ${status.message}", Toast.LENGTH_SHORT).show()
                }
                is DownloadStatus.Progress -> {
                    // Progress handled by adapter
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}