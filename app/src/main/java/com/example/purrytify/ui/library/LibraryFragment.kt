package com.example.purrytify.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.R
import com.example.purrytify.databinding.DialogAddSongBinding
import com.example.purrytify.databinding.FragmentLibraryBinding
import com.example.purrytify.model.Song
import com.example.purrytify.utils.FilePickerHelper
import com.example.purrytify.utils.showToast
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter
    private lateinit var filePickerHelper: FilePickerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

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

        setupFilePickerHelper()
        setupAdapter()
        setupRecyclerView()
        setupTabs()
        setupFabAddSong()
        observeViewModel()
    }

    private fun setupFilePickerHelper() {
        filePickerHelper = FilePickerHelper(this) { song ->
            viewModel.insertSong(song)
            requireContext().showToast("Song imported successfully")
        }
    }

    private fun setupAdapter() {
        songAdapter = SongAdapter(
            onItemClick = { song ->
                viewModel.playSong(song)
            },
            onLikeClick = { song ->
                viewModel.toggleLike(song)
            }
        )
    }

    private fun setupRecyclerView() {
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setTab(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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


    // Update the dialog handling code:

    private fun showAddSongDialog() {
        val dialogBinding = DialogAddSongBinding.inflate(layoutInflater)

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle("Add New Song")
            .setPositiveButton("Add") { dialog, _ ->
                val title = dialogBinding.etTitle.text.toString().trim()
                val artist = dialogBinding.etArtist.text.toString().trim()

                // Using safe references to UI elements that may not exist
                val audioUrl = try {
                    dialogBinding.etAudioUrl?.text?.toString()?.trim() ?: ""
                } catch (e: Exception) {
                    ""
                }

                val albumArtUrl = try {
                    dialogBinding.etAlbumArtUrl?.text?.toString()?.trim() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (title.isNotEmpty() && artist.isNotEmpty()) {
                    val newSong = Song(
                        id = "", // Use string ID instead of int
                        title = title,
                        artist = artist,
                        path = audioUrl,
                        coverUrl = albumArtUrl,
                        duration = 0,
                        isLiked = false,
                        lastPlayed = 0
                    )
                    viewModel.insertSong(newSong)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            updateEmptyState(songs)
        }
    }

    private fun updateEmptyState(songs: List<Song>) {
        if (songs.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.rvSongs.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.rvSongs.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.library_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchSongs(newText ?: "")
                return true
            }
        })

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}