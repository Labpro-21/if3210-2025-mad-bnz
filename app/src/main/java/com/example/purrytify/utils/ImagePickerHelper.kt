package com.example.purrytify.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
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

                try {
                    fragment.requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.e("ImagePickerHelper", "Failed to get persistable permission", e)
                }
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
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imagePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("ImagePickerHelper", "Error launching picker", e)
            Toast.makeText(fragment.requireContext(),
                "Could not open image picker: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }
    fun setCallback(callback: (Uri) -> Unit) {
        this.onImageSelected = callback
    }


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