package com.example.purrytify.ui.profile.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemTopArtistBinding
import com.example.purrytify.model.analytics.TopArtistStats

class TopArtistsAdapter : ListAdapter<TopArtistStats, TopArtistsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = getItem(position)
        holder.bind(artist, position)
    }

    inner class ViewHolder(private val binding: ItemTopArtistBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(artist: TopArtistStats, position: Int) {
            binding.apply {
                tvRank.text = String.format("%02d", position + 1)
                tvArtistName.text = artist.name
                
                Glide.with(ivTopArtist)
                    .load(artist.imageUrl)
                    .placeholder(R.drawable.profile_placeholder)
                    .error(R.drawable.profile_placeholder)
                    .circleCrop()
                    .into(ivTopArtist)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<TopArtistStats>() {
        override fun areItemsTheSame(oldItem: TopArtistStats, newItem: TopArtistStats) = 
            oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: TopArtistStats, newItem: TopArtistStats) = 
            oldItem == newItem
    }
}