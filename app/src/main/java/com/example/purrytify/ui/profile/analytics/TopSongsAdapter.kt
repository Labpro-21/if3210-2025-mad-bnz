package com.example.purrytify.ui.profile.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemTopSongBinding
import com.example.purrytify.model.analytics.TopSongStats

class TopSongsAdapter : ListAdapter<TopSongStats, TopSongsAdapter.ViewHolder>(SongDiffCallback()) {

    inner class ViewHolder(private val binding: ItemTopSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(song: TopSongStats) {
            binding.apply {
                tvRank.text = String.format("%02d", bindingAdapterPosition + 1)
                tvSongTitle.text = song.title
                tvArtistName.text = song.artist
                tvPlayCount.text = "• ${song.playCount} plays"
                
                Glide.with(ivTopSong)
                    .load(song.imageUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .transform(RoundedCorners(8))
                    .into(ivTopSong)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemTopSongBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class SongDiffCallback : DiffUtil.ItemCallback<TopSongStats>() {
        override fun areItemsTheSame(oldItem: TopSongStats, newItem: TopSongStats): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: TopSongStats, newItem: TopSongStats): Boolean {
            return oldItem == newItem
        }
    }
}