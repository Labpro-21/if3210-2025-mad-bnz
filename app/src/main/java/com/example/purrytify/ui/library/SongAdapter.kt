package com.example.purrytify.ui.library

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemSongBinding
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager

class SongAdapter(
    private val onItemClick: (Song) -> Unit,
    private val onLikeClick: (Song) -> Unit,
    private val onPlayClick: (Song) -> Unit,
    private val musicPlayerManager: MusicPlayerManager
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
            
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.ivLike.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLikeClick(getItem(position))
                }
            }

            binding.btnPlayPause.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val song = getItem(position)
                    if (musicPlayerManager.currentSong.value?.id == song.id) {
                        musicPlayerManager.togglePlayPause()
                    } else {
                        onPlayClick(song)
                    }
                }
            }
        }

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.ivLike.setImageResource(
                if (song.isLiked) R.drawable.ic_liked
                else R.drawable.ic_like
            )

            Log.d("SongAdapter", "Binding song: ${song.title} (${song.id})")
//            musicPlayerManager.logFullState()

            val isCurrentSong = musicPlayerManager.currentSong.value?.id == song.id
            val isPlaying = musicPlayerManager.isPlaying.value == true


            binding.btnPlayPause.setImageResource(
                if (isCurrentSong && isPlaying) R.drawable.ic_pause
                else R.drawable.ic_play
            )
            Glide.with(binding.ivAlbum.context)
                .load(song.coverUrl.takeIf { !it.isNullOrEmpty() })
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.placeholder_album)
                .centerCrop()
                .into(binding.ivAlbum)

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song)

        holder.itemView.setOnClickListener {
            musicPlayerManager.playSong(song)
            try {
                val navController = holder.itemView.findNavController()
                val bundle = Bundle().apply {
                    putSerializable("song", song)
                }
                navController.navigate(R.id.playerFragment, bundle)
            } catch (e: Exception) {
                Log.e("SongAdapter", "Navigation failed", e)
            }
        }
    }
}