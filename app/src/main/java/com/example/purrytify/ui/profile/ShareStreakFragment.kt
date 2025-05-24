package com.example.purrytify.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentShareStreakBinding
import com.example.purrytify.model.analytics.SongStreakStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale


@AndroidEntryPoint
class ShareStreakFragment : Fragment() {
    private var _binding: FragmentShareStreakBinding? = null
    private val binding get() = _binding!!
    private val streak: SongStreakStats? by lazy {
        arguments?.getParcelable("streak")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShareStreakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        streak?.let { updatePreview(it) }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnShare.setOnClickListener {
            streak?.let { captureAndShare(it) }
        }
    }

    private fun updatePreview(streak: SongStreakStats) {
        binding.apply {
            tvStreakTitle.text = "My ${streak.daysCount}-day streak 🔥"
            tvSongTitle.text = streak.songTitle
            tvArtist.text = streak.artist
            tvDate.text = getDateRange(streak.startDate, streak.endDate)

            Glide.with(requireContext())
                .load(streak.image)
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.placeholder_album)
                .transform(RoundedCorners(16))
                .into(ivSongCover)
        }
    }

    private fun getDateRange(startDate: Long, endDate: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

        return if (yearFormat.format(startDate) == yearFormat.format(endDate)) {
            "${dateFormat.format(startDate)}-${dateFormat.format(endDate)}, ${yearFormat.format(endDate)}"
        } else {
            "${dateFormat.format(startDate)}, ${yearFormat.format(startDate)}-" +
                    "${dateFormat.format(endDate)}, ${yearFormat.format(endDate)}"
        }
    }

    private fun captureAndShare(streak: SongStreakStats) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                binding.progressBar.isVisible = true
                binding.btnShare.isEnabled = false

                // Capture the share content
                val bitmap = withContext(Dispatchers.Default) {
                    val view = binding.scrollView.findViewById<View>(R.id.shareContent)
                    Bitmap.createBitmap(
                        view.width,
                        view.height,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        Canvas(this).apply {
                            // Set background color if needed
                            drawColor(resources.getColor(R.color.white, null))
                            view.draw(this)
                        }
                    }
                }

                // Save and share
                withContext(Dispatchers.IO) {
                    val imagesFolder = File(requireContext().cacheDir, "images").apply {
                        mkdirs()
                    }
                    val imageFile = File(imagesFolder, "streak_${System.currentTimeMillis()}.png")

                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    val contentUri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        imageFile
                    )

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val targetIntents = arrayListOf<Intent>().apply {
                        // Instagram Stories
                        add(Intent("com.instagram.share.ADD_TO_STORY").apply {
                            setDataAndType(contentUri, "image/*")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        })

                        // Line
                        add(Intent("jp.naver.line.android.activity.selectchat.SelectChatActivity").apply {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                        })

                        // Twitter
                        add(Intent(Intent.ACTION_SEND).apply {
                            `package` = "com.twitter.android"
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                        })

                        // WhatsApp
                        add(Intent(Intent.ACTION_SEND).apply {
                            `package` = "com.whatsapp"
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                        })
                    }

                    withContext(Dispatchers.Main) {
                        val chooserIntent = Intent.createChooser(shareIntent, "Share Streak")
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toTypedArray())
                        startActivity(chooserIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareStreakFragment", "Error sharing", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to share image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.isVisible = false
                binding.btnShare.isEnabled = true
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}