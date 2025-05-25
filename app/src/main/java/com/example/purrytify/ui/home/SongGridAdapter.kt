package com.example.purrytify.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemSongGridBinding
import com.example.purrytify.model.Song
import com.example.purrytify.player.MusicPlayerManager
import com.example.purrytify.utils.toFormattedDuration

class SongGridAdapter(
    private val onItemPlay: (Song) -> Unit,
    private val onItemClick: (Song) -> Unit,
    private val musicPlayerManager: MusicPlayerManager
) :
    ListAdapter<Song, SongGridAdapter.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))

        holder.itemView.setOnClickListener {
            musicPlayerManager.playSong(getItem(position)) // Play song directly
        }
    }

    inner class SongViewHolder(private val binding: ItemSongGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Click on whole item to play song
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val song = getItem(position)
                    musicPlayerManager.playSong(song)
                }
            }

            // Click on album art to navigate to player
            binding.ivAlbum.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val song = getItem(position)
                    musicPlayerManager.playSong(song)
                    onItemClick(song) // This will navigate to player fragment
                }
            }
        }

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = song.duration.toFormattedDuration()

            Glide.with(binding.ivAlbum)
                .load(song.coverUrl)
                .placeholder(R.drawable.placeholder_album)
                .error(R.drawable.placeholder_album)
                .transform(RoundedCorners(16))
//                .centerCrop()
                .into(binding.ivAlbum)
        }

    }

    private class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}