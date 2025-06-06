package com.example.purrytify.auth
import android.util.Log

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.purrytify.databinding.ActivityLoginBinding
import com.example.purrytify.model.ApiResponse
import com.example.purrytify.ui.MainActivity
import com.example.purrytify.utils.showToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
//
//        if (viewModel.isLoggedIn()) {
//            navigateToMain()
//            return
//        }

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
//            val email = "13522001@std.stei.itb.ac.id"
//            val password = "13522001"

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email and password must be filled")
                return@setOnClickListener
            }

            viewModel.login(email, password)
        }
    }

    private fun observeViewModel() {
    viewModel.loginState.observe(this) { state ->
        Log.d("LoginActivity", "Login state changed: $state")
        
        when (state) {
            is ApiResponse.Loading -> {
                binding.btnLogin.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
            }
            is ApiResponse.Success -> {
                binding.progressBar.visibility = View.GONE
                val serviceIntent = Intent(this, TokenRefreshService::class.java)
                startService(serviceIntent)
                navigateToMain()
            }
            is ApiResponse.Error -> {
                binding.btnLogin.isEnabled = true
                binding.progressBar.visibility = View.GONE
                showToast(state.message)
            }
        }
    }
}
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}