package com.example.purrytify.ui.profile


import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentEditProfileBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.LocationHelper
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var imagePickerHelper: ImagePickerHelper
    private lateinit var locationHelper: LocationHelper
    private var selectedImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupImagePicker()
        setupLocationPicker()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            btnEditPhoto.setOnClickListener {
                showImagePickerDialog()
            }

            tilLocation.setEndIconOnClickListener {
                showLocationPicker()
            }

            btnSave.setOnClickListener {
                saveChanges()
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Profile Photo")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> imagePickerHelper.launchCamera()
                    1 -> imagePickerHelper.pickImage()
                }
            }
            .show()
    }

    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageSelected = { uri ->
                selectedImageUri = uri
                binding.ivProfilePhoto.setImageURI(uri)
            }
        )
    }

    private fun setupLocationPicker() {
        locationHelper = LocationHelper(
            fragment = this,
            onLocationReceived = { countryCode ->
                binding.etLocation.setText(countryCode)
            }
        )
    }

    private fun showLocationPicker() {
        val options = arrayOf("Use Current Location", "Choose on Map", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Set Location")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> locationHelper.getCurrentLocation()
                    1 -> locationHelper.openMapPicker()
                }
            }
            .show()
    }

    private fun saveChanges() {
        val location = binding.etLocation.text.toString()

        viewModel.updateProfile(
            location = location.takeIf { it.isNotBlank() },
            imageUri = selectedImageUri
        )
    }

    private fun observeViewModel() {
        viewModel.updateProfileState.observe(viewLifecycleOwner) { response ->
            when (response) {
                is ApiResponse.Success<*> -> {
                    hideLoading()
                    findNavController().navigateUp()
                }
                is ApiResponse.Error -> {
                    hideLoading()
                    showToast(response.message)
                }
                is ApiResponse.Loading -> showLoading()
                else -> { /* no-op */ }
            }
        }
    }

    private fun updateUI(user: User) {
        binding.apply {
            etLocation.setText(user.location)

            Glide.with(requireContext())
                .load("${viewModel.getBaseUrl()}/uploads/profile/${user.profilePhoto}")
                .placeholder(R.drawable.profile_placeholder)
                .transform(CircleCrop())
                .into(ivProfilePhoto)
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnSave.isEnabled = true
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}