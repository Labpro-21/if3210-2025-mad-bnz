package com.example.purrytify.ui.player

import android.app.AlertDialog
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.DialogAddSongBinding
import com.example.purrytify.databinding.FragmentPlayerBinding
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.player.PlayerViewModel
import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.utils.FilePickerHelper
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.ContextCompat.getSystemService
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val playerViewModel: PlayerViewModel by viewModels()


    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager  // Inject directly instead of inheriting
    private var isSeekBarTracking = false
    private var qrBitmap: Bitmap? = null
    private val TAG = "PlayerFragment"
//    private var timeUpdateJob: Job? = null


    // Add these properties
    private lateinit var filePickerHelper: FilePickerHelper
    private lateinit var imagePickerHelper: ImagePickerHelper
    private var selectedImageUri: Uri? = null
    private var selectedAudioUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)

        // Add menu to toolbar

        binding.btnMore.setOnClickListener { showOptionsMenu(it) }


        setupHelpers()
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val songId = arguments?.getString("songId")
        Log.e("PlayerFragment", "Song ID: $songId")

        if (songId != null) {
            fetchAndPlaySongById(songId)
        }
        Log.e("PlayerFragmentss", "Song ID: $songId")

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }
//        val songFromArgs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            arguments?.getSerializable("song", Song::class.java)
//        } else {
//            @Suppress("DEPRECATION")
//            arguments?.getSerializable("song") as? Song
//        }
//        if (songFromArgs != null) {
//            updateSongUI(songFromArgs)
//            if (musicPlayerManager.currentSong.value?.id != songFromArgs.id) {
//                musicPlayerManager.playSong(songFromArgs)
//            }
//        } else {
//            musicPlayerManager.currentSong.value?.let {
//                updateSongUI(it)
//            }
//        }
        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
        binding.btnPlayPause.setOnClickListener {
            musicPlayerManager.togglePlayPause()
        }
        binding.btnLike.setOnClickListener {
            playerViewModel.toggleLike()
        }
        setupUI()
        observeViewModel()
        observePlayerState()
        setupSeekBar()
//        startTimeUpdate()
    }
    private fun fetchAndPlaySongById(songId: String) {
        Log.d(TAG, "fetchAndPlaySongById: called with songId=$songId")
        val currentSongId = musicPlayerManager.currentSong.value?.id?.toString()
//        if (currentSongId == songId) {
//            Log.d(TAG, "fetchAndPlaySongById: song already playing, updating UI only")
//            musicPlayerManager.currentSong.value?.let { updateSongUI(it) }
//            return
//        }
        lifecycleScope.launch {
            try {
                playerViewModel.fetchAndPlayOnlineSong(songId) { song ->
                    if (song != null) {
                        Log.d(TAG, "fetchAndPlaySongById: Song fetched, playing song: $song")
                        musicPlayerManager.playSong(song)
                        updateSongUI(song)
                    } else {
                        Log.e(TAG, "fetchAndPlaySongById: Song not found")
                        Toast.makeText(requireContext(), "Song not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndPlaySongById: Failed to load song", e)
                Toast.makeText(requireContext(), "Failed to load song", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHelpers() {
        filePickerHelper = FilePickerHelper(this) { song ->
            playerViewModel.updateSong(song)
        }
        imagePickerHelper = ImagePickerHelper(this) { uri ->
            selectedImageUri = uri
        }
    }

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.menu_player_options, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    playerViewModel.currentSong.value?.let { showEditSongDialog(it) }
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                R.id.action_share -> {
                    playerViewModel.currentSong.value?.let { shareAction(it) }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

//    private fun shareAction(song: Song){
//        if (song == null || song.isLocal) {
//            Toast.makeText(requireContext(), "Only online songs can be shared", Toast.LENGTH_SHORT).show()
//            return
//        }
//        shareSongDeepLink(song)
//    }
//
//    private fun shareSongDeepLink(song: Song) {
//        val deepLink = "purrytify://song/${song.id}"
//
//        try {
//            // Generate QR code using ZXing
//            val qrBitmap = generateQRCode(deepLink)
//
//            if (qrBitmap != null) {
//                // Option 1: Share as text only
//                shareTextWithQR(deepLink, qrBitmap)
//
//                // Option 2: Share QR code as image
//                // shareQRCodeImage(deepLink, qrBitmap)
//            } else {
//                // Fallback to text only sharing
//                shareTextOnly(deepLink)
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error generating QR code", e)
//            shareTextOnly(deepLink)
//        }
//    }

    private fun generateQRCode(text: String): Bitmap? {
        return try {
            // Get screen dimensions
            val windowManager = requireActivity().getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)

            // Calculate QR code size (3/4 of smaller dimension)
            val width = point.x
            val height = point.y
            val dimension = (if (width < height) width else height) * 3 / 4

            // Generate QR code using ZXing
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix: BitMatrix = multiFormatWriter.encode(
                text,
                BarcodeFormat.QR_CODE,
                dimension,
                dimension
            )

            // Convert BitMatrix to Bitmap
            val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.RGB_565)
            for (x in 0 until dimension) {
                for (y in 0 until dimension) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            Log.d(TAG, "QR code generated successfully")
            bitmap

        } catch (e: WriterException) {
            Log.e(TAG, "Error generating QR code", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code", e)
            null
        }
    }

    private fun shareTextWithQR(deepLink: String, qrBitmap: Bitmap) {
        try {
            // Show QR code dialog first, then let user choose sharing method
            showQRCodeDialog(qrBitmap, deepLink)

        } catch (e: Exception) {
            Log.e(TAG, "Error in shareTextWithQR", e)
            Toast.makeText(requireContext(), "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            // Fallback to text only
            shareTextOnly(deepLink)
        }
    }
    private fun shareImageWithText(deepLink: String, qrBitmap: Bitmap) {
        try {
            val qrImageUri = saveQRCodeToTemp(qrBitmap)

            showQRCodeDialog(qrBitmap, deepLink)
            Log.e("queryImageUri", qrImageUri.toString())
            if (qrImageUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, qrImageUri)
                    putExtra(
                        Intent.EXTRA_TEXT, """
                        🎵 Check out this song on Purrytify!
                        
                        📱 Scan the QR code or use this link:
                        $deepLink
                        
                        🎧 Enjoy listening!
                    """.trimIndent()
                    )
                    putExtra(Intent.EXTRA_SUBJECT, "🎵 Song from Purrytify")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Song with QR Code")
                startActivity(chooser)

            } else {
                shareTextOnly(deepLink)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing image with text", e)
            shareTextOnly(deepLink)
        }
    }


    private fun shareQRCodeImage(deepLink: String, qrBitmap: Bitmap) {
        try {
            // Save QR code to temporary file
            val qrImageUri = saveQRCodeToTemp(qrBitmap)
            showQRCodeDialog(qrBitmap, deepLink)

            if (qrImageUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, qrImageUri)
                    putExtra(Intent.EXTRA_TEXT, "🎵 Listen to this song on Purrytify!\n\nScan the QR code or use this link: $deepLink")
                    putExtra(Intent.EXTRA_SUBJECT, "Share Song from Purrytify")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share QR Code")
                startActivity(chooser)

                Toast.makeText(requireContext(), "QR code shared successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to save QR code to temp file")
                // Fallback to text sharing
                shareTextOnly(deepLink)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing QR image", e)
            Toast.makeText(requireContext(), "Failed to share QR code", Toast.LENGTH_SHORT).show()
            // Fallback to text sharing
            shareTextOnly(deepLink)
        }
    }

    private fun saveQRCodeToTemp(bitmap: Bitmap): Uri? {
        return try {
            val filename = "purrytify_qr_${System.currentTimeMillis()}.png"
            val file = File(requireContext().cacheDir, filename)

            // Ensure cache directory exists
            if (!requireContext().cacheDir.exists()) {
                requireContext().cacheDir.mkdirs()
            }

            val outputStream = FileOutputStream(file)

            // Use higher quality compression for sharing
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            if (!compressed) {
                Log.e(TAG, "Failed to compress bitmap")
                return null
            }

            // Verify file was created and has content
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "QR code file not created properly")
                return null
            }

            Log.d(TAG, "QR code saved to temp file: ${file.absolutePath}, size: ${file.length()} bytes")

            // Use FileProvider for sharing
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error saving QR code to temp file", e)
            null
        }
    }

    private fun shareTextOnly(deepLink: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, deepLink)
                putExtra(Intent.EXTRA_SUBJECT, "Share Song from Purrytify")
            }

            val chooser = Intent.createChooser(shareIntent, "Share Song")
            startActivity(chooser)

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing text", e)
            Toast.makeText(requireContext(), "Failed to share song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQRCodeDialog(qrBitmap: Bitmap, deepLink: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qr_code, null)
        val qrImageView = dialogView.findViewById<ImageView>(R.id.ivQRCode)
        qrImageView.setImageBitmap(qrBitmap)

        AlertDialog.Builder(requireContext())
            .setTitle("Share Song")
            .setMessage("Choose how to share this song")
            .setView(dialogView)
            .show()
    }


    private fun saveQRCodeToGallery(bitmap: Bitmap) {
        try {
            val filename = "Purrytify_QR_${System.currentTimeMillis()}.png"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // For Android 10 and above
                val resolver = requireContext().contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Purrytify")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { imageUri ->
                    resolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }

                    // Clear the pending flag
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)

                    Toast.makeText(requireContext(), "QR code saved to Gallery!", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(requireContext(), "Failed to save QR code", Toast.LENGTH_SHORT).show()
                }
            } else {
                // For Android 9 and below - need WRITE_EXTERNAL_STORAGE permission
                @Suppress("DEPRECATION")
                val savedImageURL = MediaStore.Images.Media.insertImage(
                    requireContext().contentResolver,
                    bitmap,
                    filename,
                    "Purrytify song QR code"
                )

                if (savedImageURL != null) {
                    Toast.makeText(requireContext(), "QR code saved to Gallery!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save QR code", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving QR code to gallery", e)
            Toast.makeText(requireContext(), "Failed to save QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Enhanced share action with better UX
    private fun shareAction(song: Song) {
        if (song.isLocal) {
            Toast.makeText(requireContext(), "Only online songs can be shared", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading while generating QR code
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage("Generating QR code...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // Generate QR code in background
        lifecycleScope.launch {
            try {
                delay(100) // Small delay for UI

                val deepLink = "purrytify://song/${song.id}"
                val qrBitmap = generateQRCode(deepLink)

                loadingDialog.dismiss()

                if (qrBitmap != null) {
                    // Show options dialog
                    showSharingOptionsDialog(deepLink, qrBitmap)
                } else {
                    shareTextOnly(deepLink)
                }

            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e(TAG, "Error in shareAction", e)
                Toast.makeText(requireContext(), "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showSharingOptionsDialog(deepLink: String, qrBitmap: Bitmap) {
        val options = arrayOf(
            "🔗 Share Link Only",
            "📱🔗 Share Both QR & Link",
            "💾 Save QR to Gallery"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Sharing Method")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareTextOnly(deepLink)
                    1 -> shareImageWithText(deepLink, qrBitmap)
                    2 -> saveQRCodeToGallery(qrBitmap)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSongDialog(song: Song) {
        val dialogBinding = DialogAddSongBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // Pre-fill current song data
        dialogBinding.apply {
            etTitle.setText(song.title)
            etArtist.setText(song.artist)

            // Load current cover art
            Glide.with(requireContext())
                .load(song.coverUrl)
                .placeholder(R.drawable.ic_music_note)
                .into(ivAlbumPreview)
        }

        dialogBinding.btnSelectAudio.setOnClickListener {
            filePickerHelper.pickAudioFile()
        }

        dialogBinding.btnSelectCover.setOnClickListener {
            imagePickerHelper.pickImage()
        }

        dialogBinding.btnAdd.setText("Update") // Change button text
        dialogBinding.btnAdd.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString().trim()
            val artist = dialogBinding.etArtist.text.toString().trim()
            val audioUrl = selectedAudioUri?.toString() ?: song.path
            val coverUrl = selectedImageUri?.toString() ?: song.coverUrl

            if (title.isNotEmpty() && artist.isNotEmpty()) {
                val updatedSong = song.copy(
                    title = title,
                    artist = artist,
                    path = audioUrl,
                    coverUrl = coverUrl
                )
                playerViewModel.updateSong(updatedSong)
                dialog.dismiss()
                requireContext().showToast("Song updated successfully")
            } else {
                requireContext().showToast("Title and artist are required")
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete this song?")
            .setPositiveButton("Delete") { _, _ ->
                playerViewModel.currentSong.value?.let { song ->
                    playerViewModel.deleteSong(song)
                    findNavController().navigateUp()
                    requireContext().showToast("Song deleted successfully")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun setupUI() {

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnPlayPause.setOnClickListener {
            musicPlayerManager.togglePlayPause()
        }
        binding.btnLike.setOnClickListener {
            musicPlayerManager.currentSong.value?.let { song ->
                playerViewModel.toggleLike(song)
            }
        }
        binding.btnNext.setOnClickListener {
            musicPlayerManager.playNextSong()
        }

        binding.btnPrevious.setOnClickListener {
            musicPlayerManager.playPreviousSong()
        }

        binding.btnShuffle.setOnClickListener {
            musicPlayerManager.toggleShuffle()
        }
        binding.btnRepeat.setOnClickListener {
            musicPlayerManager.toggleRepeat()
        }

        binding.btnLike.setOnClickListener {
            musicPlayerManager.currentSong.value?.let { currentSong ->
                playerViewModel.toggleLike(currentSong)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    musicPlayerManager.seekTo(it.progress)
                }
                isSeekBarTracking = false
            }
        })
    }

    private fun observeViewModel() {
        playerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.tvTitle.text = song.title
                binding.tvArtist.text = song.artist
                Glide.with(this)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .into(binding.ivAlbumArt)

                binding.btnLike.setImageResource(
                    if (song.isLiked) R.drawable.ic_liked
                    else R.drawable.ic_like
                )
                binding.seekBar.max = song.duration.toInt()
                binding.tvTotalTime.text = formatDuration(song.duration)

//                if (!song.isLocal || !song.isDownloaded){
//                    binding.btnMore.visibility = View.INVISIBLE
//                }
            }
        }
        playerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause
                else R.drawable.ic_play
            )
        }
        playerViewModel.likedSongs.observe(viewLifecycleOwner) { likedSongs ->
            musicPlayerManager.currentSong.value?.let { currentSong ->
                val isLiked = likedSongs.any { it.id == currentSong.id }
                updateLikeButtonState(isLiked)
            }
        }
        playerViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (!binding.seekBar.isPressed) {
                binding.seekBar.progress = position.toInt()
                binding.tvCurrentTime.text = formatDuration(position.toLong())
            }
        }
    }

    private fun updateLikeButtonState(isLiked: Boolean) {
        binding.btnLike.setImageResource(
            if (isLiked) R.drawable.ic_liked
            else R.drawable.ic_like
        )
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatDuration((progress * 1000).toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTracking = true
//                musicPlayerManager.pausePositionUpdates()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    try {
                        musicPlayerManager.seekTo(it.progress * 1000)
                    } catch (e: Exception) {
                        Log.e("PlayerFragment", "Seek error", e)
                    }
                }
                isSeekBarTracking = false
            }
        })
    }

    private fun formatDuration(duration: Long): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }


    private fun observePlayerState() {

        lifecycleScope.launch {
            try{
                musicPlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
                    song?.let { updateSongUI(it) } ?: findNavController().navigateUp()
                }

                musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
                    binding.btnPlayPause.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }

                musicPlayerManager.currentPosition.observe(viewLifecycleOwner) { position ->
                    if (!isSeekBarTracking) {
                        binding.tvCurrentTime.text = formatDuration(position.toLong())
                        binding.seekBar.progress = position / 1000
                    }
                }

                musicPlayerManager.isShuffleEnabled.observe(viewLifecycleOwner) { isShuffleEnabled ->
                    binding.btnShuffle.setImageResource(
                        if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
                    )
                }

                musicPlayerManager.songDuration.observe(viewLifecycleOwner) { duration ->
                    binding.tvTotalTime.text = formatDuration(duration)
                    binding.seekBar.max = (duration / 1000).toInt() // Convert milliseconds to seconds
                }

                musicPlayerManager.repeatMode.observe(viewLifecycleOwner) { repeatMode ->
                    val iconRes = when (repeatMode) {
                        MusicPlayerManager.RepeatMode.OFF -> R.drawable.ic_repeat
                        MusicPlayerManager.RepeatMode.REPEAT_ALL -> R.drawable.ic_repeat_all
                        MusicPlayerManager.RepeatMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                    }
                    binding.btnRepeat.setImageResource(iconRes)
                }

            } catch (e : Exception){
                Log.e("PlayerFragment", "Error observing currentSong", e)
            }
        }







    }

    private fun updateSongUI(song: Song) {
        Log.e("PlayerFragment", "Updating UI with song: $song")
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist
        Glide.with(requireContext())
            .load(song.coverUrl.takeIf { !it.isNullOrEmpty() })
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .into(binding.ivAlbumArt)
        playerViewModel.isLiked(song.id).observe(viewLifecycleOwner) { isLiked ->
            updateLikeButtonState(isLiked)
        }
    }

    private fun formatTime(millis: Int): String {
        val minutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return String.format("%02d:%02d", minutes, seconds)
    }

//    private fun startTimeUpdate() {
//        timeUpdateJob?.cancel() // Cancel any existing job
//        timeUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
//            try {
//                while (true) {
//                    if (!isSeekBarTracking && isActive) {
//                        val position = musicPlayerManager.getCurrentPosition()
//                        binding.tvCurrentTime.text = formatTime(position)
//                        binding.seekBar.progress = position / 1000 // Convert to seconds
//                    }
//                    delay(1000)
//                }
//            } catch (e: Exception) {
//                // Handle any errors
//                e.printStackTrace()
//            }
//        }
//    }


    override fun onResume() {
        super.onResume()
        musicPlayerManager.setPlayerFragmentVisible(true)
    }

    override fun onPause() {
        super.onPause()
        musicPlayerManager.setPlayerFragmentVisible(false)
    }

    override fun onDestroyView() {
//        timeUpdateJob?.cancel()
        selectedImageUri = null
        selectedAudioUri = null
        _binding = null
        // Don't cancel player operations here
        super.onDestroyView()
    }

    override fun onStop() {
        super.onStop()
//        timeUpdateJob?.cancel() // Cancel UI updates only
    }

}