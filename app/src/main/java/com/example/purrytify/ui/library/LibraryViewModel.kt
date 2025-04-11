package com.example.purrytify.ui.library

import androidx.lifecycle.*
import com.example.purrytify.model.Song
import com.example.purrytify.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {
    private val _currentTab = MutableLiveData(0)
    val currentTab: LiveData<Int> = _currentTab

    // Add this for search functionality
    private val _searchQuery = MutableLiveData("")

    // Modified songs LiveData to handle both tabs and search
    val songs = _currentTab.switchMap { tab ->
        _searchQuery.switchMap { query ->
            when {
                query.isEmpty() -> when (tab) {
                    0 -> songRepository.getAllSongs().asLiveData()
                    1 -> songRepository.getLikedSongs().asLiveData()
                    else -> songRepository.getAllSongs().asLiveData()
                }
                else -> liveData {
                    val allSongs = when (tab) {
                        0 -> songRepository.getAllSongs().first()
                        1 -> songRepository.getLikedSongs().first()
                        else -> songRepository.getAllSongs().first()
                    }
                    emit(allSongs.filter { song ->
                        song.title.contains(query, ignoreCase = true) ||
                                song.artist.contains(query, ignoreCase = true)
                    })
                }
            }
        }
    }

    fun setTab(position: Int) {
        _currentTab.value = position
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            songRepository.updateLastPlayed(song.id, System.currentTimeMillis())
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            songRepository.updateSong(song.copy(isLiked = !song.isLiked))
        }
    }

    fun insertSong(song: Song) {
        viewModelScope.launch {
            songRepository.addLocalSong(song)
        }
    }


    fun searchSongs(query: String) {
        _searchQuery.value = query
    }
}