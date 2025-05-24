package com.example.purrytify.ui.library

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.DialogAddSongBinding
import com.example.purrytify.databinding.FragmentLibraryBinding
import com.example.purrytify.model.Song
import com.example.purrytify.ui.common.BaseFragment
import com.example.purrytify.utils.FilePickerHelper
import com.example.purrytify.utils.ImagePickerHelper
import com.example.purrytify.utils.MediaHelper
import com.example.purrytify.utils.showToast
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class LibraryFragment : BaseFragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter
    private lateinit var filePickerHelper: FilePickerHelper
    private lateinit var imagePickerHelper: ImagePickerHelper
    private var selectedImageUri: android.net.Uri? = null
    private var selectedAudioUri: android.net.Uri? = null
    private var searchEditText: EditText? = null
    private var emptyStateTextView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchEditText = view.findViewById(R.id.etSearch)


        setupAdapter()
        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupSortButton()
        setupFilePickerHelper()
        setupImagePickerHelper()
        setupFabAddSong()
        observeViewModel()
    }

    private fun setupFilePickerHelper() {
        filePickerHelper = FilePickerHelper(this) { song ->
            viewModel.insertSong(song)
        }
    }

    private fun setupImagePickerHelper() {
        imagePickerHelper = ImagePickerHelper(this) { uri ->
            selectedImageUri = uri
        }
    }

    private fun setupAdapter() {
        songAdapter = SongAdapter(
            onNavigateToPlayer = { song ->
                findNavController().navigate(
                    R.id.action_libraryFragment_to_playerFragment,
                    Bundle().apply {
                        putSerializable("song", song)
                    }
                )
            },
            onLikeClick = { song ->
                viewModel.toggleLike(song)
            },
            musicPlayerManager = musicPlayerManager
        )

        // Keep these observers
        musicPlayerManager.currentSong.observe(viewLifecycleOwner) { _ ->
            songAdapter.notifyDataSetChanged()
        }

        musicPlayerManager.isPlaying.observe(viewLifecycleOwner) { _ ->
            songAdapter.notifyDataSetChanged()
        }
    }

    private fun setupRecyclerView() {
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songAdapter
        }
        binding.rvSongs.apply {
            val divider = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
            ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)?.let {
                divider.setDrawable(it)
            }
            addItemDecoration(divider)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.position?.let { viewModel.setTab(it) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        view?.findViewById<View>(R.id.btnSearch)?.setOnClickListener {
            val isSearchVisible = searchEditText?.visibility == View.VISIBLE
            searchEditText?.visibility = if (isSearchVisible) View.GONE else View.VISIBLE

            if (!isSearchVisible) {
                searchEditText?.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                searchEditText?.let { editText ->
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                searchEditText?.text?.clear()
                viewModel.search("")
            }
        }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            popup.menu.add("Sort by Title")
            popup.menu.add("Sort by Artist")
            popup.menu.add("Sort by Date Added")
            popup.menu.add("Sort by Recently Played")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title.toString()) {
                    "Sort by Title" -> {
                        viewModel.setSortOrder(LibraryViewModel.SortOrder.TITLE)
                        true
                    }
                    "Sort by Artist" -> {
                        viewModel.setSortOrder(LibraryViewModel.SortOrder.ARTIST)
                        true
                    }
                    "Sort by Date Added" -> {
                        viewModel.setSortOrder(LibraryViewModel.SortOrder.DATE_ADDED)
                        true
                    }
                    "Sort by Recently Played" -> {
                        viewModel.setSortOrder(LibraryViewModel.SortOrder.RECENTLY_PLAYED)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
    }

    private fun setupFabAddSong() {
        binding.fabAddSong.setOnClickListener {
            showAddSongOptions()
        }
    }
    private fun showAddSongOptions() {
        val options = arrayOf("Import local file", "Add song manually")
        AlertDialog.Builder(requireContext())
            .setTitle("Add Song")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> filePickerHelper.pickAudioFile()
                    1 -> showAddSongDialog()
                }
            }
            .show()
    }


    private fun showAddSongDialog() {
        val dialogBinding = DialogAddSongBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        selectedAudioUri = null
        selectedImageUri = null
        var songDuration: Long = 0 // Add variable to store duration

        val originalCallback = filePickerHelper.getCallback()

        dialogBinding.btnSelectAudio.setOnClickListener {
            filePickerHelper.setCallback { song ->


                selectedAudioUri = Uri.parse(song.path)

                dialogBinding.etTitle.setText(song.title)
                dialogBinding.etArtist.setText(song.artist)
                songDuration = song.duration // Store the song duration

                if (song.coverUrl.toString().isNotEmpty()) {
                    selectedImageUri = Uri.parse(song.coverUrl)
                    Glide.with(requireContext())
                        .load(selectedImageUri)
                        .placeholder(R.drawable.ic_music_note)
                        .into(dialogBinding.ivAlbumPreview)
                }
            }
            filePickerHelper.pickAudioFile()
        }


        val originalImageCallback = imagePickerHelper.getCallback()


        dialogBinding.btnSelectCover.setOnClickListener {

            imagePickerHelper.setCallback { uri ->
                selectedImageUri = uri

                Glide.with(requireContext())
                    .load(uri)
                    .placeholder(R.drawable.ic_music_note)
                    .into(dialogBinding.ivAlbumPreview)
            }
            imagePickerHelper.pickImage()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val title = dialogBinding.etTitle.text.toString().trim()
            val artist = dialogBinding.etArtist.text.toString().trim()
            val audioUrl = selectedAudioUri.toString()
            val albumArtUrl = selectedImageUri?.toString()

            if (title.isNotEmpty() && artist.isNotEmpty() && audioUrl.isNotEmpty()) {
                // Get duration from MediaMetadataRetriever if not already set
                if (songDuration == 0L) {
                    songDuration = selectedAudioUri?.let { uri ->
                        MediaHelper.getAudioDuration(requireContext(), uri)
                    } ?: 0L
                }

                val newSong = Song(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    artist = artist,
                    path = audioUrl,
                    coverUrl = albumArtUrl,
                    duration = songDuration, // Use the actual duration
                    isDownloaded = false,
                    isLiked = false,
                    lastPlayed = 0,
                    isLocal = true,
                )
                viewModel.insertSong(newSong)
                requireContext().showToast("Song added successfully")
                dialog.dismiss()
            } else {
                requireContext().showToast("Title and artist are required")
            }
        }
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnCancel.setOnClickListener {

            filePickerHelper.setCallback(originalCallback)
            imagePickerHelper.setCallback(originalImageCallback)
            dialog.dismiss()
        }


        dialog.setOnDismissListener {
            filePickerHelper.setCallback(originalCallback)
            imagePickerHelper.setCallback(originalImageCallback)
        }

        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            updateEmptyState(songs.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (emptyStateTextView != null) {
            emptyStateTextView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        } else {
            val textView = view?.findViewById<TextView>(R.id.tvEmptyState)
            textView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
    }

//    private fun showSongOptionsMenu(song: Song, view: View) {
//        val popupMenu = PopupMenu(requireContext(), view)
//        popupMenu.menu.add("Play")
//        popupMenu.menu.add("Delete")
//
//        popupMenu.setOnMenuItemClickListener { item ->
//            when (item.title) {
//                "Play" -> {
////                    viewModel.playSong(song)
//                    true
//                }
//                "Delete" -> {
//                    viewModel.deleteSong(song)
//                    true
//                }
//                else -> false
//            }
//        }
//
//        popupMenu.show()
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchEditText = null
        emptyStateTextView = null
        _binding = null
    }
}