package com.example.purrytify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.audio.AudioDeviceManager
import com.example.purrytify.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var audioDeviceManager: AudioDeviceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Update UI with current device on start
        binding.tvOutputDevice.text = audioDeviceManager.getCurrentDevice().name

        // Observe changes
        audioDeviceManager.currentDevice.observe(viewLifecycleOwner) { device ->
            binding.tvOutputDevice.text =audioDeviceManager.getCurrentDevice().name
        }
        setupBackButton()
        setupSettingsOptions()
        setupAudioDeviceSection()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSettingsOptions() {
        binding.optionOutput.setOnClickListener {
            showAudioDeviceDialog()
        }

        binding.optionAbout.setOnClickListener {
            showAboutDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setupAudioDeviceSection() {
        // Observe current device
        audioDeviceManager.currentDevice.observe(viewLifecycleOwner) { device ->
            binding.tvOutputDevice.text = device.name
        }

        // Observe errors
        audioDeviceManager.connectionError.observe(viewLifecycleOwner) { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showAudioDeviceDialog() {
        AudioDeviceDialog().show(
            childFragmentManager,
            "AudioDeviceDialog"
        )
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About Purrytify")
            .setMessage("Version 1.0\n\nPurrytify is a music player app designed for MAD.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.logout()
                findNavController().navigate(R.id.action_settingsFragment_to_loginActivity)
                activity?.finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}