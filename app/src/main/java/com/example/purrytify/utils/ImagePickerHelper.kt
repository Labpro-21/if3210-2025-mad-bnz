package com.example.purrytify.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class ImagePickerHelper(
    private val fragment: Fragment,
    private var onImageSelected: (Uri) -> Unit
) {

    private val imagePickerLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Take persistent URI permission for this URI
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                fragment.requireContext().contentResolver.takePersistableUriPermission(uri, flags)

                onImageSelected(uri)
            }
        }
    }

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            fragment.requireContext().showToast("Storage permission denied")
        }
    }

    fun pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun setCallback(callback: (Uri) -> Unit) {
        this.onImageSelected = callback
    }

    // Method to get the current callback
    fun getCallback(): (Uri) -> Unit {
        return onImageSelected
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }
}