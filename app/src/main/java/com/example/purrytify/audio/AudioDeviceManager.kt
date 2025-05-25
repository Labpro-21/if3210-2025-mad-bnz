package com.example.purrytify.audio

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purrytify.player.MusicPlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicPlayerManager: MusicPlayerManager
) {
    private val TAG = "AudioDeviceManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private val _availableDevices = MutableLiveData<List<AudioDevice>>()
    val availableDevices: LiveData<List<AudioDevice>> = _availableDevices
    
    private val _currentDevice = MutableLiveData<AudioDevice>()
    val currentDevice: LiveData<AudioDevice> = _currentDevice
    
    private val _connectionError = MutableLiveData<String>()
    val connectionError: LiveData<String> = _connectionError

    private var bluetoothDevices = mutableListOf<BluetoothDevice>()
    private var bluetoothProfile: BluetoothProfile? = null

    private var bluetoothA2dp: BluetoothA2dp? = null

    init {
//        initializeBluetoothProxy()
        observeBluetoothChanges()
//        scanForDevices()
        getCurrentDevice()
    }

    fun getCurrentDevice(): AudioDevice {
        val devices = getAllAudioDevicesStatus()
        val activeDevice = devices.find { it.isConnected }

        if (activeDevice != null) {
            Log.d("ActiveDevice", "Current: ${activeDevice.type} - ${activeDevice.name}")
            return activeDevice
        } else {
            Log.d("ActiveDevice", "Default Speaker")
        }
        return AudioDevice(
            id = "internal_speaker",
            name = "Phone Speaker",
            type = AudioDeviceType.INTERNAL_SPEAKER,
            isConnected = true
        )
    }
    private fun getAllAudioDevicesStatus(): List<AudioDevice> {
        val deviceList = mutableListOf<AudioDevice>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            devices.forEach { device ->
                val isActive = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> audioManager.isBluetoothA2dpOn
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> audioManager.isWiredHeadsetOn
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> !audioManager.isWiredHeadsetOn && !audioManager.isBluetoothA2dpOn
                    else -> false
                }

                deviceList.add(
                    AudioDevice(
                        id = device.id.toString(),
                        name = device.productName.toString(),
                        type = getDeviceTypeName(device.type),
                        isConnected = isActive,
                    )
                )
            }
        }

        return deviceList
    }
    private fun getDeviceTypeName(type: Int): AudioDeviceType {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.INTERNAL_SPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->AudioDeviceType.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceType.WIRED
            AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceType.WIRED
            else -> AudioDeviceType.INTERNAL_SPEAKER
        }
    }



    private fun getInternalSpeakerDevice() = AudioDevice(
        id = "internal_speaker",
        name = "Phone Speaker",
        type = AudioDeviceType.INTERNAL_SPEAKER,
        isConnected = true
    )

    private fun updateCurrentDevice(device: AudioDevice) {
        Log.d(TAG, "Current audio device updated to: ${device.name} (${device.type})")
        _currentDevice.postValue(device)
    }


    private fun observeBluetoothChanges() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            if (it.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                                updateCurrentDevice(
                                    AudioDevice(
                                        id = it.address,
                                        name = it.name ?: "Bluetooth Device",
                                        type = AudioDeviceType.BLUETOOTH,
                                        isConnected = true
                                    )
                                )
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // Fallback to internal speaker when Bluetooth disconnects
                        updateCurrentDevice(getInternalSpeakerDevice())
                    }
                }
            }
        }, filter)
    }

    fun scanForDevices() {
        Log.d(TAG, "Starting device scan")
        try {
            val devices = mutableListOf<AudioDevice>()
            val current = getCurrentDevice()

            // Get available devices using the same method as device selection
            val audioDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.availableCommunicationDevices
            } else {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            }

            Log.d(TAG, "Found ${audioDevices.size} audio devices")

            // Always add internal speaker if available
            audioDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
                devices.add(AudioDevice(
                    id = it.id.toString(),
                    name = "Phone Speaker",
                    type = AudioDeviceType.INTERNAL_SPEAKER,
                    isConnected = current.type == AudioDeviceType.INTERNAL_SPEAKER
                ))
            }

            // Add Bluetooth devices
            audioDevices.filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }.forEach {
                devices.add(AudioDevice(
                    id = it.id.toString(),
                    name = it.productName?.toString() ?: "Bluetooth Device",
                    type = AudioDeviceType.BLUETOOTH,
                    isConnected = current.type == AudioDeviceType.BLUETOOTH
                ))
            }

            // Add wired devices
            audioDevices.filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }.forEach {
                devices.add(AudioDevice(
                    id = it.id.toString(),
                    name = it.productName?.toString() ?: "Wired Headphones",
                    type = AudioDeviceType.WIRED,
                    isConnected = current.type == AudioDeviceType.WIRED
                ))
            }

            Log.d(TAG, "Processed devices: ${devices.map { "${it.name} (${it.type})" }}")
            _availableDevices.postValue(devices)

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning devices", e)
            _connectionError.postValue("Error scanning audio devices")
        }
    }




//    fun selectDevice(device: AudioDevice) {
//        Log.d(TAG, "Attempting to select device: ${device.name} (${device.type})")
//        try {
//            // Get available devices
//            val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
//
//            // Handle Bluetooth disconnection if switching to speaker
//            if (device.type == AudioDeviceType.INTERNAL_SPEAKER) {
//                Log.d(TAG, "Disconnecting Bluetooth for speaker mode")
//                bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
//                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
//                        if (profile == BluetoothProfile.A2DP) {
//                            val a2dp = proxy as BluetoothA2dp
//                            // Disconnect all active devices
//                            a2dp.connectedDevices.forEach { device ->
//                                a2dp.disconnect(device)
//                            }
//                        }
//                    }
//                    override fun onServiceDisconnected(profile: Int) {}
//                }, BluetoothProfile.A2DP)
//            }
//
//            // Find matching device info
//            val selectedDevice = when (device.type) {
//                AudioDeviceType.BLUETOOTH -> {
//                    Log.d(TAG, "Looking for Bluetooth device")
//                    availableDevices.find {
//                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
//                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
//                    }
//                }
//                AudioDeviceType.INTERNAL_SPEAKER -> {
//                    Log.d(TAG, "Looking for Internal Speaker")
//                    availableDevices.find {
//                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
//                    }
//                }
//                AudioDeviceType.WIRED -> {
//                    Log.d(TAG, "Looking for Wired device")
//                    availableDevices.find {
//                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
//                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
//                    }
//                }
//            }
//
//            if (selectedDevice != null) {
//                Log.d(TAG, "Found device: ${selectedDevice.productName}")
//                musicPlayerManager.recreatePlayer(selectedDevice)
//                _currentDevice.postValue(device)
//            } else {
//                throw Exception("Device not found")
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error switching device", e)
//            _connectionError.postValue("Failed to switch to ${device.name}")
//            fallbackToInternalSpeaker()
//        }
//    }

//    private fun fallbackToInternalSpeaker() {
//        Log.d(TAG, "Falling back to internal speaker")
//        try {
//            val internalSpeaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
//                .find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
//
//            if (internalSpeaker != null) {
//                musicPlayerManager.recreatePlayer(internalSpeaker)
//                _currentDevice.postValue(
//                    AudioDevice(
//                        id = "internal_speaker",
//                        name = "Phone Speaker",
//                        type = AudioDeviceType.INTERNAL_SPEAKER,
//                        isConnected = true
//                    )
//                )
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error falling back to internal speaker", e)
//        }
//    }



    fun selectDevice(device: AudioDevice) {
        Log.d(TAG, "Attempting to select device: ${device.name} (${device.type})")

        try {
            // Validate device availability first
            if (!isDeviceAvailable(device)) {
                Log.w(TAG, "Device ${device.name} is not available")
                _connectionError.postValue("Device ${device.name} is not available")
                return
            }

            // Get available devices
            val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Find matching device info
            val selectedDevice = findMatchingDevice(availableDevices, device)

            if (selectedDevice != null) {
                Log.d(TAG, "Found device: ${selectedDevice.productName}")

                // Apply routing strategy based on device type with error handling
                try {
                    when (device.type) {
                        AudioDeviceType.INTERNAL_SPEAKER -> {
                            switchToInternalSpeaker(selectedDevice)
                        }
                        AudioDeviceType.BLUETOOTH -> {
                            switchToBluetoothDevice(selectedDevice)
                        }
                        AudioDeviceType.WIRED -> {
                            switchToWiredDevice(selectedDevice)
                        }
                    }

                    _currentDevice.postValue(device)
                    Log.d(TAG, "Successfully switched to ${device.name}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error in device switch: ${e.message}", e)
                    handleSwitchError(device)
                }

            } else {
                Log.w(TAG, "Device ${device.name} not found in available devices")
                _connectionError.postValue("Device ${device.name} not found")
                handleSwitchError(device)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical error switching device: ${e.message}", e)
            _connectionError.postValue("Critical error: ${e.message}")

            // Emergency fallback
            try {
                fallbackToInternalSpeaker()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Emergency fallback failed", fallbackError)
            }
        }
    }

    private fun isDeviceAvailable(device: AudioDevice): Boolean {
        return when (device.type) {
            AudioDeviceType.BLUETOOTH -> {
                bluetoothAdapter?.isEnabled == true && hasConnectedBluetoothAudioDevice()
            }
            AudioDeviceType.WIRED -> {
                audioManager.isWiredHeadsetOn
            }
            AudioDeviceType.INTERNAL_SPEAKER -> {
                true // Always available
            }
        }
    }

    private fun hasConnectedBluetoothAudioDevice(): Boolean {
        if (bluetoothAdapter?.isEnabled != true) return false

        // Check for paired Bluetooth audio devices
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices.any { device ->
            val deviceClass = device.bluetoothClass
            deviceClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    deviceClass?.hasService(BluetoothClass.Service.AUDIO) == true ||
                    deviceClass?.hasService(BluetoothClass.Service.RENDER) == true
        }
    }

    private fun activateBluetoothAudioSafe(deviceInfo: AudioDeviceInfo) {
        try {
            Log.d(TAG, "Safe Bluetooth activation for: ${deviceInfo.productName}")

            // Simple approach - just update routing and let system handle activation
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    updateMediaPlayerRouting(deviceInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in delayed routing update", e)
                    handleBluetoothActivationFailure()
                }
            }, 300)

        } catch (e: Exception) {
            Log.e(TAG, "Error in safe Bluetooth activation", e)
            handleBluetoothActivationFailure()
        }
    }

    private fun handleBluetoothActivationFailure() {
        Log.w(TAG, "Bluetooth activation failed, falling back to speaker")
        try {
            fallbackToInternalSpeaker()
        } catch (e: Exception) {
            Log.e(TAG, "Even fallback failed", e)
            _connectionError.postValue("Audio routing failed")
        }
    }

    private fun findMatchingDevice(availableDevices: Array<AudioDeviceInfo>, targetDevice: AudioDevice): AudioDeviceInfo? {
        return when (targetDevice.type) {
            AudioDeviceType.BLUETOOTH -> {
                Log.d(TAG, "Looking for Bluetooth device")
                availableDevices.find { deviceInfo ->
                    (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) &&
                            isDeviceConnected(deviceInfo)
                }
            }
            AudioDeviceType.INTERNAL_SPEAKER -> {
                Log.d(TAG, "Looking for Internal Speaker")
                availableDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            }
            AudioDeviceType.WIRED -> {
                Log.d(TAG, "Looking for Wired device")
                availableDevices.find { deviceInfo ->
                    (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) &&
                            isDeviceConnected(deviceInfo)
                }
            }
        }
    }

    private fun isDeviceConnected(deviceInfo: AudioDeviceInfo): Boolean {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> audioManager.isBluetoothA2dpOn
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> audioManager.isBluetoothScoOn
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> audioManager.isWiredHeadsetOn
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
            else -> false
        }
    }

    private fun switchToInternalSpeaker(deviceInfo: AudioDeviceInfo) {
        Log.d(TAG, "Switching to internal speaker")

        // Clear any forced routing first
        audioManager.clearCommunicationDevice()
        audioManager.isSpeakerphoneOn = true
        audioManager.mode = AudioManager.MODE_NORMAL

        // Update MediaPlayer routing without recreating
        updateMediaPlayerRouting(deviceInfo)
    }

    private fun switchToBluetoothDevice(deviceInfo: AudioDeviceInfo) {
        Log.d(TAG, "Switching to Bluetooth device: ${deviceInfo.productName}")

        // Reset speaker phone mode first
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL

        // For A2DP devices, try direct routing first (simpler approach)
        if (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            Log.d(TAG, "Using direct A2DP routing")
            updateMediaPlayerRouting(deviceInfo)
        } else {
            // For SCO devices, use simplified activation
            Log.d(TAG, "Using simplified Bluetooth activation")
            activateBluetoothAudioSafe(deviceInfo)
        }
    }

    private fun switchToWiredDevice(deviceInfo: AudioDeviceInfo) {
        Log.d(TAG, "Switching to wired device: ${deviceInfo.productName}")

        // Reset speaker phone mode
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL

        // Update MediaPlayer routing
        updateMediaPlayerRouting(deviceInfo)
    }

    private fun updateMediaPlayerRouting(deviceInfo: AudioDeviceInfo) {
        try {
            Log.d(TAG, "Updating MediaPlayer routing to: ${deviceInfo.productName}")

            // Simplified approach - recreate player directly for Bluetooth to ensure stability
            if (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {

                Log.d(TAG, "Recreating player for Bluetooth device")
                musicPlayerManager.recreatePlayer(deviceInfo)

            } else {
                // For non-Bluetooth, try preferred device first
                applyMediaPlayerRouting(deviceInfo)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update MediaPlayer routing", e)
            handleRoutingError(e)
        }
    }

    private fun handleRoutingError(error: Exception) {
        Log.e(TAG, "Routing error: ${error.message}")
        try {
            fallbackToInternalSpeaker()
        } catch (e: Exception) {
            Log.e(TAG, "Critical routing failure", e)
            _connectionError.postValue("Critical audio error: ${e.message}")
        }
    }

    private fun applyMediaPlayerRouting(deviceInfo: AudioDeviceInfo) {
        try {
            // Try to update routing without recreating player first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val success = musicPlayerManager.setPreferredDevice(deviceInfo)
                if (success) {
                    Log.d(TAG, "Successfully updated routing to ${deviceInfo.productName}")
                    return
                }
            }

            // If direct routing fails, recreate player as fallback
            Log.d(TAG, "Direct routing failed, recreating player")
            musicPlayerManager.recreatePlayer(deviceInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply MediaPlayer routing", e)
            throw e // Re-throw to be handled by caller
        }
    }

    private fun handleSwitchError(targetDevice: AudioDevice) {
        Log.w(TAG, "Handling switch error for ${targetDevice.name}")

        when (targetDevice.type) {
            AudioDeviceType.BLUETOOTH -> {
                // If Bluetooth fails, try wired or fallback to speaker
                if (audioManager.isWiredHeadsetOn) {
                    Log.d(TAG, "Bluetooth failed, trying wired device")
                    tryFallbackToWired()
                } else {
                    fallbackToInternalSpeaker()
                }
            }
            AudioDeviceType.WIRED -> {
                // If wired fails, fallback to speaker
                fallbackToInternalSpeaker()
            }
            AudioDeviceType.INTERNAL_SPEAKER -> {
                // Speaker should always work, log error and continue
                Log.e(TAG, "Internal speaker failed - this should not happen")
            }
        }
    }

    private fun tryFallbackToWired() {
        try {
            val wiredDevice = AudioDevice("wired_headset","Wired Headset", AudioDeviceType.WIRED,true)
            selectDevice(wiredDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Wired fallback failed", e)
            fallbackToInternalSpeaker()
        }
    }

    private fun fallbackToInternalSpeaker() {
        Log.d(TAG, "Falling back to internal speaker")
        try {
            val speakerDevice = AudioDevice("interal_speaker","Phone Speaker", AudioDeviceType.INTERNAL_SPEAKER,true)

            // Force switch to speaker with minimal error handling
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_NORMAL

            val availableDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val speakerDeviceInfo = availableDevices.find {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }

            if (speakerDeviceInfo != null) {
                musicPlayerManager.recreatePlayer(speakerDeviceInfo)
                _currentDevice.postValue(speakerDevice)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical error: Even speaker fallback failed", e)
            _connectionError.postValue("Critical audio error")
        }
    }







//    testing algorithm


    private fun activateBluetoothA2dp(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Activating Bluetooth A2DP")

        // Get Bluetooth A2DP proxy
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp

                    // Check if any A2DP device is connected
                    val connectedDevices = a2dp.connectedDevices
                    if (connectedDevices.isNotEmpty()) {
                        Log.d(TAG, "Found ${connectedDevices.size} connected A2DP devices")

                        // Force audio routing to A2DP
                        Handler(Looper.getMainLooper()).postDelayed({
                            val isA2dpActive = audioManager.isBluetoothA2dpOn
                            Log.d(TAG, "A2DP activation result: $isA2dpActive")
                            callback(isA2dpActive)
                        }, 500) // Give time for activation

                    } else {
                        Log.w(TAG, "No A2DP devices connected")
                        callback(false)
                    }

                    // Close proxy
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                Log.w(TAG, "A2DP service disconnected")
                callback(false)
            }
        }, BluetoothProfile.A2DP)
    }


}

data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean
)

enum class AudioDeviceType {
    INTERNAL_SPEAKER,
    BLUETOOTH,
    WIRED
}