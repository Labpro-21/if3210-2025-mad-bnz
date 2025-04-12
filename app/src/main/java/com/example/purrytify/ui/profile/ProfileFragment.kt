package com.example.purrytify.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentProfileBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        viewModel.loadProfile()
        setupSettingsButton()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { state ->
                    when (state) {
                        is ApiResponse.Loading -> showLoading()
                        is ApiResponse.Success -> showProfile(state.data)
                        is ApiResponse.Error -> showError(state.message)
                    }
                }
            }
        }
        viewModel.totalSongs.observe(viewLifecycleOwner) { count ->
            binding.tvTotalSongs.text = count.toString()
        }

        viewModel.likedSongs.observe(viewLifecycleOwner) { count ->
            binding.tvLikedSongs.text = count.toString()
        }
        viewModel.listenedSongs.observe(viewLifecycleOwner) { count ->
            binding.tvListenedSongs.text = count.toString()
        }

    }
    private fun showProfile(user: User) {
        binding.tvUsername.text = user.username
        binding.tvEmail.text = user.email
        val profilePhotoUrl = if (user.profilePhoto.isNullOrEmpty()) {
            null
        } else {
            "${viewModel.getBaseUrl()}/uploads/profile-picture/${user.profilePhoto}"
        }

        Glide.with(this)
            .load(profilePhotoUrl)
            .placeholder(R.drawable.profile_placeholder)
            .error(R.drawable.profile_placeholder)
            .circleCrop()
            .into(binding.ivProfilePic)

    }

    private fun showLoading() {
        // Show loading state
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}