package com.example.purrytify.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

class ImagePickerHelper(
    private val fragment: Fragment,
    private var onImageSelected: (Uri) -> Unit
) {



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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {  // Change to ACTION_OPEN_DOCUMENT
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)  // Add these flags
            }
            imagePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("ImagePickerHelper", "Error launching picker", e)
            Toast.makeText(
                fragment.requireContext(),
                "Could not open image picker: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val imagePickerLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // Take persistent permissions
                    fragment.requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    onImageSelected(uri)
                } catch (e: Exception) {
                    Log.e("ImagePickerHelper", "Failed to get persistent permission", e)
                    // Still try to use the URI even if persistent permission fails
                    onImageSelected(uri)
                }
            }
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

    private val cameraLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            temporaryImageUri?.let(onImageSelected)
        }
    }

    private val galleryLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(onImageSelected)
    }

    private var temporaryImageUri: Uri? = null

    fun launchCamera() {
        val tempFile = File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            fragment.requireContext().cacheDir
        )
        temporaryImageUri = FileProvider.getUriForFile(
            fragment.requireContext(),
            "${fragment.requireContext().packageName}.provider",
            tempFile
        )
        cameraLauncher.launch(temporaryImageUri)
    }

    fun launchGallery() {
        galleryLauncher.launch("image/*")
    }

    fun getCompressedBitmap(context: Context, uri: Uri, maxWidthHeight: Int): Bitmap {
        // Get original dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        // Calculate sample size
        val sampleSize = calculateSampleSize(
            options.outWidth,
            options.outHeight,
            maxWidthHeight
        )

        // Decode with sample size
        options.apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
        }

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalStateException("Could not decode image")
    }

    private fun calculateSampleSize(width: Int, height: Int, maxWidthHeight: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxWidthHeight || height / sampleSize > maxWidthHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}