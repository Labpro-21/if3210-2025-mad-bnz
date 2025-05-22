package com.example.purrytify.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.BuildConfig
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide

import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.utils.Constants
import com.example.purrytify.utils.DateFormatter
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : BaseFragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var imagePickerHelper: ImagePickerHelper

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePhoto(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        setupImagePicker()
        loadData()
    }

    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageSelected = { uri ->
                handleSelectedImage(uri)
            }
        )
    }
    private fun handleSelectedImage(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        viewModel.uploadProfilePhoto(uri, requireContext())
    }
    private fun setupUI() {
        binding.btnEditPhoto.setOnClickListener {
            checkNetworkBeforeImageSelection()
            openImagePicker()
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }
        binding.cardSoundCapsule.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_soundCapsuleFragment)
        }
    }
    private fun checkNetworkBeforeImageSelection() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(),
                "Internet connection required to update profile picture",
                Toast.LENGTH_SHORT).show()
            return
        }

        imagePickerHelper.pickImage()
    }

    private fun loadData() {
        checkNetworkBeforeLoading {
            viewModel.loadProfile()
        }
    }

    override fun onReconnected() {
        viewModel.loadProfile()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { state ->
                    when (state) {
                        is ApiResponse.Loading -> showLoading()
                        is ApiResponse.Success -> showProfile(state.data)
                        is ApiResponse.Error -> showError(state.message)
                    }
                }
            }
        }

        viewModel.totalSongs.observe(viewLifecycleOwner) { count ->
            binding.tvTotalSongs.text = count.toString()
        }

        viewModel.likedSongs.observe(viewLifecycleOwner) { count ->
            binding.tvLikedSongs.text = count.toString()
        }

        viewModel.listenedSongs.observe(viewLifecycleOwner) { count ->
            binding.tvListenedSongs.text = count.toString()
        }

        viewModel.uploadProgress.observe(viewLifecycleOwner) { response ->
            when (response) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), response.data, Toast.LENGTH_SHORT).show()
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT).show()
                }
                null -> { /* Initial state, do nothing */ }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun showProfile(user: User) {
        binding.progressBar.visibility = View.GONE
        binding.tvUsername.text = user.username
        binding.tvEmail.text = user.email
        binding.tvLocation.text = getCountryName(user.location)
        binding.tvAccountId.text = user.id
        try {
            val createdDate = DateFormatter.formatApiDate(user.createdAt)
            binding.tvCreatedDate.text = createdDate
        } catch (e: Exception) {
            binding.tvCreatedDate.text = user.createdAt
        }


        val imageUrl = "${getBaseURL()}/uploads/profile-picture/${user.profilePhoto}"
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.profile_placeholder)
            .error(R.drawable.profile_placeholder)
            .into(binding.ivProfilePhoto)
    }
        fun getBaseURL():String{
            return Constants.BASE_URL
        }
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun openImagePicker() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Internet connection required to update profile photo", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun uploadProfilePhoto(uri: Uri) {
        viewModel.uploadProfilePhoto(uri, requireContext())
    }

    private fun getCountryName(countryCode: String): String {
        return when (countryCode) {
            "ID" -> "Indonesia"
            "US" -> "United States"
            "GB" -> "United Kingdom"
            "JP" -> "Japan"
            "KR" -> "South Korea"
            else -> countryCode
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}