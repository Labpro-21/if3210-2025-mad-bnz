package com.example.purrytify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

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

    // Flow collection looks correct
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.userEmail.collect { email ->
                binding.tvEmail.text = email ?: "No email available"
            }
        }
    }

    binding.btnLogout.setOnClickListener {
        viewModel.logout()
        // Use the correct ID from your navigation graph
        findNavController().navigate(R.id.action_settingsFragment_to_loginActivity)
        
        // Or use the activity ID directly
        // findNavController().navigate(R.id.login_activity)
        
        // Or use the global action
        // findNavController().navigate(R.id.navigation_to_login)
    }
}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}