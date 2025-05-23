package com.example.purrytify.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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
//            btnDownload.setOnClickListener()
//            btnShare.setOnClickListener()

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
                    tvStreakTitle.text = getString(R.string.streak_title, streak.daysCount)
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
        val shareText = """
        🎵 ${streak.daysCount}-day streak on Purrytify!
        Playing "${streak.songTitle}" by ${streak.artist} day after day.
        I was on fire! 🔥
    """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share your streak"))
    }

}