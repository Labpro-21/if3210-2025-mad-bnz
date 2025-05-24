package com.example.purrytify.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.BuildConfig
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.databinding.LayoutStreakCardBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.model.analytics.SongStreakStats

import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import com.example.purrytify.utils.Constants
import com.example.purrytify.utils.CountryUtils
import com.example.purrytify.utils.DateFormatter
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.NetworkUtils
import com.example.purrytify.utils.ViewUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import com.bumptech.glide.load.resource.bitmap.RoundedCorners as GlideRoundedCorners



@AndroidEntryPoint
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var imagePickerHelper: ImagePickerHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.resetExportState()

    }

    private fun setupUI() {
        binding.apply {

            btnEditProfile.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
            }
        }
    }
    private fun observeViewModel() {
        var isFirstLoad = true

        viewModel.exportState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ExportState.Loading -> {
                    binding.layoutSoundCapsule.btnDownload.isEnabled = false

                }
                is ExportState.Success -> {
                    binding.layoutSoundCapsule.btnDownload.isEnabled = true
//                    shareFile(state.uri)
                    showExportSuccess(state.uri)
                }
                is ExportState.Error -> {
                    binding.layoutSoundCapsule.btnDownload.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    binding.layoutSoundCapsule.btnDownload.isEnabled = true
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadProfile()
                viewModel.profile.collect { response ->
                    when (response) {
                        is ApiResponse.Loading -> {
                            if (isFirstLoad) {
                                showLoading()
                            }
                        }
                        is ApiResponse.Success -> {
                            hideLoading()
                            isFirstLoad = false
                            updateUI(response.data)
                        }
                        is ApiResponse.Error -> {
                            hideLoading()
                            isFirstLoad = false
                            showError(response.message)
                        }
                    }
                }
            }
        }

        viewModel.soundCapsule.observe(viewLifecycleOwner) { stats ->
            updateSoundCapsule(stats)
        }
    }
    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Share Sound Capsule"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to handle CSV files", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(user: User) {
        binding.tvUsername.text = user.username
        binding.tvLocation.text = CountryUtils.getCountryName(user.location)

        // Load profile image
        val imageUrl = "${viewModel.getBaseUrl()}uploads/profile-picture/${user.profilePhoto}"
        Log.e("PROFILEPRO", imageUrl)
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.profile_placeholder)
            .error(R.drawable.profile_placeholder)
            .into(binding.ivProfilePhoto)
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        binding.progressBar.visibility = View.GONE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }
    fun String.toDate(): Date {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this) ?: Date()
    }


    private fun updateSoundCapsule(stats: MonthlyStats) {
        binding.layoutSoundCapsule.apply {
            tvDate.text = stats.monthYear
            tvTimeListened.text = "${stats.totalMinutes} minutes"
            btnDownload.setOnClickListener{
                viewModel.exportToCSV()
            }
            btnShare.setOnClickListener{
                shareSoundCapsule(stats)
            }

            MoreTopArtist.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_topArtistsFragment)
            }
            layoutTopArtist.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_topArtistsFragment)
            }
            MoreTopSong.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_topSongsFragment)
            }
            layoutTopSong.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_topSongsFragment)
            }

            MoreTimeListened.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_timeListenedFragment)
            }
            layoutTimeListened.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_timeListenedFragment)
            }

            stats.currentStreak?.let { streak ->
                binding.layoutStreakCard.apply {
                    root.visibility = View.VISIBLE
                    tvStreakTitle.text = getString(R.string.streak_title, streak.daysCount) + " 🔥"
                    tvStreakDescription.text = getString(
                        R.string.streak_description,
                        streak.songTitle,
                        streak.artist
                    )

                    // Format dates using SimpleDateFormat for consistency
                    val dateFormat = SimpleDateFormat("MMM d", Locale.US)
                    val yearFormat = SimpleDateFormat("yyyy", Locale.US)

                    val startDate = streak.startDate
                    val endDate = streak.endDate

                    val dateText = if (yearFormat.format(startDate) == yearFormat.format(endDate)) {
                        "${dateFormat.format(startDate)}-${dateFormat.format(endDate)}, " +
                                yearFormat.format(endDate)
                    } else {
                        "${dateFormat.format(startDate)}, ${yearFormat.format(startDate)}-" +
                                "${dateFormat.format(endDate)}, ${yearFormat.format(endDate)}"
                    }

                    tvStreakDates.text = dateText
                    btnShare.setOnClickListener {
                        shareStreak(streak)
                    }
                    Glide.with(requireContext())
                        .load(streak.image)
                        .placeholder(R.drawable.placeholder_album)
                        .error(R.drawable.placeholder_album)
                        .transform(GlideRoundedCorners(16))
                        .into(ivTopSong)
                }
            } ?: run {
                binding.layoutStreakCard.root.visibility = View.GONE
            }


            if (stats.topArtists.isNotEmpty()) {
                tvTopArtist.text = stats.topArtists.first().name
                Glide.with(requireContext())
                    .load(stats.topArtists.first().imageUrl)
                    .placeholder(R.drawable.profile_placeholder)
                    .error(R.drawable.profile_placeholder)
                    .circleCrop()
                    .into(ivTopArtist)
            }

            if (stats.topSongs.isNotEmpty()) {
                tvTopSong.text = stats.topSongs.first().title
//                Log.e("TESTTTT",stats.toString())
                Glide.with(requireContext())
                    .load(stats.topSongs.first().imageUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .circleCrop()
                    .into(ivTopSong)
            }
        }


    }


    private fun shareStreak(streak: SongStreakStats) {
        try {
            val bundle = Bundle().apply {
                putParcelable("streak", streak)
            }
            findNavController().navigate(
                R.id.action_profileFragment_to_shareStreakFragment,
                bundle
            )
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error navigating to share streak", e)
            Toast.makeText(
                requireContext(),
                "Error opening share screen",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showExportSuccess(uri: Uri) {
        val file = uri.path?.let { File(it) }
        val message = if (file?.exists() == true) {
            "CSV saved to:\n${file.absolutePath}"
        } else {
            "CSV file saved successfully"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Successful")
            .setMessage(message)
            .setPositiveButton("Open") { _, _ ->
                openFile(uri)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun openFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "No app found to open CSV files",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareSoundCapsule(stats: MonthlyStats) {
        try {
            val bundle = Bundle().apply {
                putParcelable("stats", stats)
            }
            findNavController().navigate(
                R.id.action_profileFragment_to_shareCapsuleFragment,
                bundle
            )
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error navigating to share fragment", e)
            Toast.makeText(
                requireContext(),
                "Error opening share screen",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}