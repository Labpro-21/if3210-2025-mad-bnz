package com.example.purrytify.ui.charts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.ItemChartSongBinding
import com.example.purrytify.model.Song

class ChartSongAdapter(
    private val onPlayClick: (Song) -> Unit,
    private val onDownloadClick: (Song) -> Unit
) : ListAdapter<Song, ChartSongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var downloadProgress = mutableMapOf<String, Int>()

    fun updateDownloadProgress(songId: String, progress: Int) {
        downloadProgress[songId] = progress
        notifyItemChanged(currentList.indexOfFirst { it.id == songId })
    }

    inner class SongViewHolder(private val binding: ItemChartSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { song ->
                    onPlayClick(song)
                }
            }

            binding.btnDownload.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { song ->
                    onDownloadClick(song)
                }
            }
        }

        fun bind(song: Song, position: Int) {
            binding.apply {
                tvRank.text = (position + 1).toString()
                tvTitle.text = song.title
                tvArtist.text = song.artist

                val progress = this@ChartSongAdapter.downloadProgress[song.id] ?: 0
                btnDownload.apply {
                    isVisible = !song.isLocal
                    isEnabled = !song.isDownloaded


                    when {
                        song.isDownloaded -> {
                            btnDownload.setImageResource(R.drawable.ic_downloaded)
                            btnDownload.isEnabled = false
                            downloadProgress.visibility = View.GONE
                        }
                        progress in 1..99 -> {
                            btnDownload.visibility = View.GONE
                            downloadProgress.visibility = View.VISIBLE
                        }
                        else -> {
                            btnDownload.setImageResource(R.drawable.ic_download)
                            btnDownload.isEnabled = true
                            btnDownload.visibility = View.VISIBLE
                            downloadProgress.visibility = View.GONE
                        }
                    }

                    setOnClickListener {
                        if (!song.isDownloaded && progress == 0) {
                            onDownloadClick(song)
                        }
                    }
                }

                // Load artwork
                Glide.with(ivArtwork)
                    .load(song.coverUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .centerCrop()
                    .into(ivArtwork)
            }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemChartSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        getItem(position)?.let { song ->
            holder.bind(song, position)
        }
    }
}