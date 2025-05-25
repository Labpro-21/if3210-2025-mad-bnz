package com.example.purrytify.ui.profile


import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentEditProfileBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import com.example.purrytify.utils.CountryUtils
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.LocationHelper
import com.example.purrytify.utils.LocationHelper.Companion.PLACE_PICKER_REQUEST
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log


@AndroidEntryPoint
class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var imagePickerHelper: ImagePickerHelper
    private lateinit var locationHelper: LocationHelper
    private var selectedImageUri: Uri? = null

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
            else -> {
                showLocationPermissionDeniedDialog()
            }
        }
    }

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
        observeViewModel()
        setupLocationButton()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            btnEditPhoto.setOnClickListener {
                showPhotoOptions()
            }

            btnSave.setOnClickListener {
                saveChanges()
            }

            btnLogout.setOnClickListener {
                showLogoutConfirmationDialog()
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.logout()
                findNavController().navigate(R.id.navigation_to_login)
                activity?.finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun setupImagePicker() {
        imagePickerHelper = ImagePickerHelper(
            fragment = this,
            onImageSelected = { uri ->
                try {
                    // Take persistent permissions if needed
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    selectedImageUri = uri
                    // Update image preview
                    Glide.with(this)
                        .load(uri)
                        .transform(CircleCrop())
                        .into(binding.ivProfilePhoto)
                } catch (e: Exception) {
                    Log.e("EditProfileFragment", "Error handling selected image", e)
                    Toast.makeText(requireContext(), "Error selecting image", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }


    private fun saveChanges() {
        val location = CountryUtils.getCountryCode(binding.tvLocation.text.toString()).toString()

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
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is ApiResponse.Error -> {
                    hideLoading()
                    Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT).show()
                }
                is ApiResponse.Loading -> showLoading()
                null -> {}
            }
        }
        var isFirstLoad = true

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(user:User) {
        binding.apply {
            Log.d("EditProfileFragment", "Updating UI with user: $user")
            tvLocation.setText(CountryUtils.getCountryName(user.location))

            Glide.with(requireContext())
                .load("${viewModel.getBaseUrl()}uploads/profile-picture/${user.profilePhoto}")
                .placeholder(R.drawable.profile_placeholder)
                .transform(CircleCrop())
                .into(ivProfilePhoto)
        }
    }

    private fun showLoading() {

        binding.btnSave.isEnabled = false
    }

    private fun hideLoading() {

        binding.btnSave.isEnabled = true
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun setupLocationButton() {
        binding.btnSetLocation.setOnClickListener {
            showLocationOptions()
        }
    }

    private fun showLocationOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Location")
            .setItems(arrayOf("Use Current Location", "Input Manual")) { _, which ->
                when (which) {
                    0 -> checkLocationPermission()
                    1 -> showManualLocationInputDialog()
                }
            }
            .show()
    }
    private fun showManualLocationInputDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Enter country or city"
            setSingleLine()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Input Location Manually")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val manualLocation = input.text.toString().trim()
                if (manualLocation.isNotEmpty()) {
                    handleManualLocation(manualLocation)
                } else {
                    showLocationError("Location cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleManualLocation(location: String) {
        // Validasi: bisa pakai CountryUtils jika ingin hanya negara tertentu
        val countryCode = CountryUtils.getCountryCode(location)
        if (countryCode != null && CountryUtils.isCountrySupported(countryCode)) {
            binding.tvLocation.text = CountryUtils.getCountryName(countryCode)
            viewModel.updateLocation(countryCode)
        } else {
            // Jika ingin menerima apapun, bisa langsung set location tanpa validasi
            binding.tvLocation.text = location
            viewModel.updateLocation(location)
            // Atau tampilkan error jika tidak valid
            // showLocationError("Location not supported or not recognized")
        }
    }

    private fun openPlacePicker() {
        try {
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN, fields
            ).setTypeFilter(TypeFilter.REGIONS).build(requireContext())
            startActivityForResult(intent, PLACE_PICKER_REQUEST)
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "Error launching place picker", e)
            Toast.makeText(context, "Error opening location picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> getCurrentLocation()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

//    private fun getCurrentLocation() {
//        if (hasLocationPermission()) {
//            fusedLocationClient.lastLocation
//                .addOnSuccessListener { location ->
//                    location?.let {
//                        lifecycleScope.launch(Dispatchers.IO) {
//
//                            getAddressFromLocation(it)
//                        }
//                    } ?: showLocationError("Couldn't get current location")
//                }
//                .addOnFailureListener {
//                    showLocationError("Error getting location: ${it.message}")
//                }
//        }
//    }

    private fun getCurrentLocation() {
        if (hasLocationPermission()) {

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->

                    location?.let {
                        // This location is the current device location
                        lifecycleScope.launch {
                            getAddressFromLocation(it)
                        }
                    } ?: showLocationError("Couldn't get current location. Please try again.")
                }
                .addOnFailureListener { e ->

                    showLocationError("Error getting location: ${e.message}")
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PLACE_PICKER_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val place = Autocomplete.getPlaceFromIntent(data)
                    place.address?.let { address ->
                        handleSelectedLocation(address)
                    }
                }
            }
            MAP_PICKER_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Handle map selection if needed
                }
            }
        }
    }

    private fun handleSelectedLocation(address: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(address, 1)
            addresses?.firstOrNull()?.let {
                val countryCode = it.countryCode
                if (CountryUtils.isCountrySupported(countryCode)) {
                    binding.tvLocation.text = countryCode
                    viewModel.updateLocation(countryCode)
                } else {
                    showLocationError("Location not supported: $countryCode")
                }
            }
        } catch (e: Exception) {
            showLocationError("Error getting location details")
        }
    }

    companion object {
        private const val PLACE_PICKER_REQUEST = 1001
        private const val MAP_PICKER_REQUEST = 1002  // Add this constant
    }
    private fun updateProfileImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.profile_placeholder)
            .error(R.drawable.profile_placeholder)
            .into(binding.ivProfilePhoto)
    }

    private suspend fun getAddressFromLocation(location: Location) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            withContext(Dispatchers.Main) {
                addresses?.firstOrNull()?.let { address ->
                    val countryCode = address.countryCode
                    Log.e("Check country",countryCode)
                    if (CountryUtils.isCountrySupported(countryCode)) {
                        binding.tvLocation.text = CountryUtils.getCountryName(countryCode)
                    } else {
                        showLocationError("Location not supported: $countryCode")
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showLocationError("Error getting address: ${e.message}")
            }
        }
    }

    private fun openMapPicker() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("geo:0,0?q=")
            setPackage("com.google.android.apps.maps")
        }

        try {
            startActivityForResult(intent, MAP_PICKER_REQUEST)
        } catch (e: ActivityNotFoundException) {
            showLocationError("Google Maps is not installed")
        }
    }



    private fun showLocationPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Permission Required")
            .setMessage("We need location permission to automatically set your country. Would you like to grant permission?")
            .setPositiveButton("Grant") { _, _ ->
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Permission Denied")
            .setMessage("You can still set your location manually using the map option.")
            .setPositiveButton("Open Map") { _, _ -> openMapPicker() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private var temporaryPhotoUri: Uri? = null



    private fun showPhotoOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Photo")
            .setItems(arrayOf("Take Photo", "Choose from Gallery")) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> imagePickerHelper.pickImage()
                }
            }
            .show()
    }

    private fun takePhoto() {
        try {
            // Buat file temporary untuk foto
            val photoFile = createTempImageFile()
            temporaryPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )

            // Launch camera
            temporaryPhotoUri?.let { uri ->
                takePictureLauncher.launch(uri)
            }
        } catch (e: Exception) {

        }
    }

    private fun createTempImageFile(): File {
        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            requireContext().cacheDir
        ).apply {
            deleteOnExit() // Hapus file saat aplikasi ditutup
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            temporaryPhotoUri?.let { uri ->
                try {
                    // Compress dan resize foto
                    val bitmap = imagePickerHelper.getCompressedBitmap(
                        requireContext(),
                        uri,
                        maxWidthHeight = 1024
                    )
                    // Update UI dan simpan
                    binding.ivProfilePhoto.setImageBitmap(bitmap)
                    viewModel.updateProfile(null,uri)
                    // Hapus file temporary
                    requireContext().contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
//                    Log.e(TAG, "Error processing camera photo", e)
//                    showError("Failed to process photo")
                }
            }
        }
    }

}