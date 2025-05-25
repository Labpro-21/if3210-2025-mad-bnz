package com.example.purrytify.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purrytify.R
import com.example.purrytify.audio.AudioDevice
import com.example.purrytify.audio.AudioDeviceType
import com.example.purrytify.databinding.ItemAudioDeviceBinding

class AudioDeviceAdapter(
    private val onDeviceSelected: (AudioDevice) -> Unit
) : ListAdapter<AudioDevice, AudioDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemAudioDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemAudioDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeviceSelected(getItem(position))
                }
            }
        }

        fun bind(device: AudioDevice) {
            binding.apply {
                tvDeviceName.text = device.name
                tvDeviceType.text = when (device.type) {
                    AudioDeviceType.BLUETOOTH -> "Bluetooth"
                    AudioDeviceType.WIRED -> "Wired"
                    AudioDeviceType.INTERNAL_SPEAKER -> "Internal"
                }

                ivDeviceIcon.setImageResource(
                    when (device.type) {
                        AudioDeviceType.BLUETOOTH -> R.drawable.ic_bluetooth_device
                        AudioDeviceType.WIRED -> R.drawable.ic_headphone
                        AudioDeviceType.INTERNAL_SPEAKER -> R.drawable.ic_speaker
                    }
                )

                ivConnectionStatus.setImageResource(
                    if (device.isConnected) R.drawable.ic_connected
                    else R.drawable.ic_disconnected
                )
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<AudioDevice>() {
        override fun areItemsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
            return oldItem == newItem
        }
    }
}