package com.example.purrytify.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentShareCapsuleBinding
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class ShareCapsuleFragment : Fragment() {
    private var _binding: FragmentShareCapsuleBinding? = null
    private val binding get() = _binding!!
    private var webView: WebView? = null
    private val stats: MonthlyStats? by lazy {
        arguments?.getParcelable("stats")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShareCapsuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()

        stats?.let { updatePreview(it) }
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            btnShare.setOnClickListener {
                stats?.let { captureAndShare(it) }
            }
        }
    }



    private fun generateShareableHtml(stats: MonthlyStats): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        color: white;
                        background-color: #121212;
                        padding: 20px;
                        margin: 0;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                    }
                    .header {
                        display: flex;
                        align-items: center;
                        margin-bottom: 20px;
                    }
                    .logo {
                        width: 40px;
                        height: 40px;
                        margin-right: 10px;
                    }
                    .title {
                        font-size: 24px;
                        font-weight: bold;
                        margin-bottom: 10px;
                    }
                    .stats {
                        display: flex;
                        justify-content: space-between;
                        margin-top: 20px;
                    }
                    .stat-section {
                        flex: 1;
                    }
                    .stat-title {
                        color: #b3b3b3;
                        margin-bottom: 10px;
                    }
                    .minutes {
                        color: #1DB954;
                        font-size: 32px;
                        font-weight: bold;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <img src="file:///android_res/drawable/ic_purrytify_logo_round" class="logo">
                        <span>${stats.monthYear}</span>
                    </div>
                    <div class="title">My Sound Capsule</div>
                    <div class="stats">
                        <div class="stat-section">
                            <div class="stat-title">Top Artists</div>
                            ${stats.topArtists.take(5).mapIndexed { index, artist ->
                                "${index + 1}. ${artist.name}"
                            }.joinToString("<br>")}
                        </div>
                        <div class="stat-section">
                            <div class="stat-title">Top Songs</div>
                            ${stats.topSongs.take(5).mapIndexed { index, song ->
                                "${index + 1}. ${song.title}"
                            }.joinToString("<br>")}
                        </div>
                    </div>
                    <div class="minutes">${stats.totalMinutes} minutes</div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun captureWebViewToImage(webView: WebView, callback: (Bitmap) -> Unit) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.body.getBoundingClientRect().height") { height ->
                    val bitmap = Bitmap.createBitmap(
                        view.width,
                        height.toFloat().toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    callback(bitmap)
                }
            }
        }
    }

    private fun shareToSocialMedia(bitmap: Bitmap) {
        try {
            val imagesFolder = File(requireContext().cacheDir, "images").apply {
                mkdirs()
            }
            val file = File(imagesFolder, "sound_capsule_${System.currentTimeMillis()}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Create share sheet with specific targets
            val targetShareIntents = mutableListOf<Intent>()

            // Instagram Stories
            Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(contentUri, "image/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }.also { targetShareIntents.add(it) }

            // Line
            Intent("jp.naver.line.android.activity.selectchat.SelectChatActivity").apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }.also { targetShareIntents.add(it) }

            // Default share
            val chooserIntent = Intent.createChooser(shareIntent, "Share Sound Capsule")
            chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                targetShareIntents.toTypedArray()
            )

            startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e("ShareCapsuleFragment", "Error sharing", e)
            Toast.makeText(
                requireContext(),
                "Failed to share image: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updatePreview(stats: MonthlyStats) {
        try {
            binding.apply {
                layoutShareCapsule.apply {
                    Log.e("soundstats",stats.toString())
                    tvDate.text = stats.monthYear
                    tvTimeListened.text = "${stats.totalMinutes} minutes"

                    val topArtistsText = stats.topArtists.take(5).mapIndexed { index, artist ->
                        "${index + 1}. ${artist.name}"
                    }.joinToString("\n")
                    tvTopArtists.text = topArtistsText

                    val topSongsText = stats.topSongs.take(5).mapIndexed { index, song ->
                        "${index + 1}. ${song.title}"
                    }.joinToString("\n")
                    tvTopSongs.text = topSongsText

                    stats.topSongs.firstOrNull()?.imageUrl?.let { imageUrl ->
                        Glide.with(requireContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.placeholder_album)
                            .error(R.drawable.placeholder_album)
                            .transform(RoundedCorners(16))
                            .into(ivTopSong)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ShareCapsuleFragment", "Error updating preview", e)
            Toast.makeText(
                requireContext(),
                "Error updating preview",
                Toast.LENGTH_SHORT
            ).show()
        }

//        val html = generateShareableHtml(stats)
//        webView?.loadDataWithBaseURL(
//            "file:///android_res/",
//            html,
//            "text/html",
//            "UTF-8",
//            null
//        )
//
//        binding.btnShare.setOnClickListener {
//            webView?.let { web ->
//                binding.progressBar.isVisible = true
//                binding.btnShare.isEnabled = false
//
//                captureWebViewToImage(web) { bitmap ->
//                    shareToSocialMedia(bitmap)
//                    binding.progressBar.isVisible = false
//                    binding.btnShare.isEnabled = true
//                }
//            }
//        }
    }

    private fun captureAndShare(stats: MonthlyStats) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                binding.progressBar.isVisible = true
                binding.btnShare.isEnabled = false

                // Capture the view
                val bitmap = withContext(Dispatchers.Default) {
                    val view = binding.scrollView.findViewById<View>(R.id.layoutShareCapsule)
                    Bitmap.createBitmap(
                        view.width,
                        view.height,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        Canvas(this).apply {
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
                    val imageFile = File(imagesFolder, "sound_capsule_${System.currentTimeMillis()}.png")

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

                    // Create share sheet with specific targets
                    val targetShareIntents = mutableListOf<Intent>()

                    // Instagram Stories
                    Intent("com.instagram.share.ADD_TO_STORY").apply {
                        setDataAndType(contentUri, "image/*")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }.also { targetShareIntents.add(it) }

                    // Line
                    Intent("jp.naver.line.android.activity.selectchat.SelectChatActivity").apply {
                        action = Intent.ACTION_SEND
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                    }.also { targetShareIntents.add(it) }

                    Intent(Intent.ACTION_SEND).apply {
                        `package` = "com.twitter.android"
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                    }.also { targetShareIntents.add(it) }

                    // WhatsApp
                   Intent(Intent.ACTION_SEND).apply {
                        `package` = "com.whatsapp"
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                   }.also { targetShareIntents.add(it) }

                    withContext(Dispatchers.Main) {
                        val chooserIntent = Intent.createChooser(shareIntent, "Share Sound Capsule")
                        chooserIntent.putExtra(
                            Intent.EXTRA_INITIAL_INTENTS,
                            targetShareIntents.toTypedArray()
                        )
                        startActivity(chooserIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("ShareCapsuleFragment", "Error sharing", e)
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