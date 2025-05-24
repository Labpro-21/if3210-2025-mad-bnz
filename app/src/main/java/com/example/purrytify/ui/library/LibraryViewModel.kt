package com.example.purrytify.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
) : ViewModel() {
    enum class SortOrder {
        TITLE, ARTIST, DATE_ADDED, RECENTLY_PLAYED
    }

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _currentTab = MutableLiveData(0)
    val currentTab: LiveData<Int> = _currentTab

    private val _searchQuery = MutableLiveData("")

    private val _sortOrder = MutableLiveData(SortOrder.DATE_ADDED)
    val sortOrder: LiveData<SortOrder> = _sortOrder

    init {
        loadSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            songRepository.getOfflineSongs().collectLatest { songList ->
                _songs.value = applySortAndFilter(songList)
            }
        }
    }

    fun setTab(tabIndex: Int) {
        _currentTab.value = tabIndex
        viewModelScope.launch {
            when (tabIndex) {
                0 -> songRepository.getOfflineSongs().collectLatest { songs ->
                    Log.e("LIBRAryViewModel", "Songs: $songs")
                    _songs.value = applySortAndFilter(songs)
                }
                1 -> songRepository.getLikedSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }
                2 -> songRepository.getDownloadedSongs().collectLatest { songs ->
                _songs.value = applySortAndFilter(songs)
                }
                else -> songRepository.getOfflineSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            when (_currentTab.value) {
                0 -> songRepository.getOfflineSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }
                1 -> songRepository.getLikedSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }

                2 -> songRepository.getDownloadedSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }
                else -> songRepository.getOfflineSongs().collectLatest { songs ->
                    _songs.value = applySortAndFilter(songs)
                }
            }
        }
    }

    private fun applySortAndFilter(songs: List<Song>): List<Song> {
        val filteredSongs = if (_searchQuery.value.isNullOrBlank()) {
            songs
        } else {
            val query = _searchQuery.value!!.lowercase()
            songs.filter {
                it.title.lowercase().contains(query) ||
                it.artist.lowercase().contains(query)
            }
        }

        return when (_sortOrder.value) {
            SortOrder.TITLE -> filteredSongs.sortedBy { it.title }
            SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist }
            SortOrder.RECENTLY_PLAYED -> filteredSongs.sortedByDescending { it.lastPlayed }
            SortOrder.DATE_ADDED, null -> filteredSongs.sortedByDescending { it.createdAt }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        _songs.value = applySortAndFilter(_songs.value ?: emptyList())
    }


    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val updatedSong = song.copy(isLiked = !song.isLiked)
            songRepository.updateSong(updatedSong)
        }
    }

    fun insertSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            songRepository.addLocalSong(song)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            songRepository.deleteSong(song.id)
        }
    }
}