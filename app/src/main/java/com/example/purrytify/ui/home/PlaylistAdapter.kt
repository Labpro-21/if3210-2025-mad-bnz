package com.example.purrytify.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemPlaylistBinding
import com.example.purrytify.model.PlaylistRecommendation


class PlaylistAdapter(
    private val onPlaylistClick: (PlaylistRecommendation) -> Unit
) : ListAdapter<PlaylistRecommendation, PlaylistAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlaylistClick(getItem(position))
                }
            }
        }

        fun bind(playlist: PlaylistRecommendation) {
            binding.apply {
                tvPlaylistName.text = playlist.name
                tvPlaylistDescription.text = playlist.description
                
                Glide.with(ivPlaylistCover)
                    .load(playlist.imageUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .transform(RoundedCorners(16))
                    .into(ivPlaylistCover)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemPlaylistBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<PlaylistRecommendation>() {
        override fun areItemsTheSame(oldItem: PlaylistRecommendation, newItem: PlaylistRecommendation) = 
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PlaylistRecommendation, newItem: PlaylistRecommendation) = 
            oldItem == newItem
    }
}