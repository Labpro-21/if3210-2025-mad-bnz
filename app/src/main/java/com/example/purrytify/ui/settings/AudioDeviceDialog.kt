package com.example.purrytify.ui.settings

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purrytify.R
import com.example.purrytify.audio.AudioDeviceManager
import com.example.purrytify.databinding.DialogAudioDeviceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


import android.content.Intent
import android.provider.Settings


@AndroidEntryPoint
class AudioDeviceDialog : DialogFragment() {
    private var _binding: DialogAudioDeviceBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var audioDeviceManager: AudioDeviceManager
    
    private lateinit var deviceAdapter: AudioDeviceAdapter

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false) -> {
                // Permission granted, scan devices
                binding.progressBar.visibility = View.VISIBLE
                audioDeviceManager.scanForDevices()
            }
            else -> {
                // Permission denied
                Snackbar.make(
                    binding.root,
                    "Bluetooth permission required to scan audio devices",
                    Snackbar.LENGTH_LONG
                ).setAction("Settings") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    })
                }.show()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.Theme_Dialog).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioDeviceManager.scanForDevices()
        setStyle(STYLE_NORMAL, R.style.Theme_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAudioDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeDevices()
        
        // Check and request permissions before scanning
        binding.btnRefresh.setOnClickListener {
            checkAndRequestPermissions()
        }
        
        // Initial scan with permission check
        checkAndRequestPermissions()
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = AudioDeviceAdapter { device ->
            audioDeviceManager.selectDevice(device)
            dismiss()
        }
        
        binding.rvDevices.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }
    
    private fun observeDevices() {
        // Observe available devices
        audioDeviceManager.availableDevices.observe(viewLifecycleOwner) { devices ->
            if (devices.isEmpty()) {
                binding.apply {
                    progressBar.visibility = View.GONE
                    rvDevices.visibility = View.GONE
                    tvNoDevices.visibility = View.VISIBLE
                    tvNoDevices.text = "No audio devices found"
                }
            } else {
                binding.apply {
                    progressBar.visibility = View.GONE
                    rvDevices.visibility = View.VISIBLE
                    tvNoDevices.visibility = View.GONE
                }
                deviceAdapter.submitList(devices)
            }
        }
        
        // Observe errors
        audioDeviceManager.connectionError.observe(viewLifecycleOwner) { error ->
            binding.progressBar.visibility = View.GONE
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when {
                requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, scan devices
                    binding.progressBar.visibility = View.VISIBLE
                    audioDeviceManager.scanForDevices()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) -> {
                    // Show rationale if needed
                    showPermissionRationale()
                }
                else -> {
                    // Request permission
                    bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                }
            }
        } else {
            // For older Android versions
            binding.progressBar.visibility = View.VISIBLE
            audioDeviceManager.scanForDevices()
        }
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bluetooth Permission Required")
            .setMessage("To scan for audio devices, we need permission to access Bluetooth features.")
            .setPositiveButton("Grant") { _, _ ->
                bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}