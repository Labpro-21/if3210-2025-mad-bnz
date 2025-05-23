package com.example.purrytify.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.util.Locale
import android.Manifest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationHelper(
    private val fragment: Fragment,
    private val onLocationReceived: (String) -> Unit
) {
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(fragment.requireActivity())
    }

    private val locationPermissionRequest = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                getCurrentLocation()
            }
            else -> {
                showLocationPermissionDeniedDialog()
            }
        }
    }

    fun getCurrentLocation() {
        if (hasLocationPermission()) {
            requestLocation()
        } else {
            requestLocationPermission()
        }
    }

    fun openMapPicker() {
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?z=2")
            }
            fragment.startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(fragment.context, "No map application found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return permissions.all { permission ->
            fragment.requireContext().checkSelfPermission(permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    getCountryCode(it.latitude, it.longitude)
                }
            }
    }

    private fun getCountryCode(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(fragment.requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.countryCode?.let { countryCode ->
                onLocationReceived(countryCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLocationPermissionDeniedDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Location Permission Required")
            .setMessage("Please enable location permission to use this feature")
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireContext().packageName, null)
            fragment.startActivity(this)
        }
    }

    companion object {
        const val PLACE_PICKER_REQUEST = 999
    }
}