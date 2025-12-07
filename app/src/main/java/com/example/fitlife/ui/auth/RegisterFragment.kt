package com.example.fitlife.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.databinding.FragmentRegisterBinding
import com.example.fitlife.utils.SessionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        firebaseAuth = Firebase.auth

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (validateInput(name, email, password, confirmPassword)) {
                performRegistration(name, email, password)
            }
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun validateInput(name: String, email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_empty_field)
            isValid = false
        } else {
            binding.tilName.error = null
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_short_password)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_dont_match)
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun performRegistration(name: String, email: String, password: String) {
        showLoading(true)

        val app = requireActivity().application as FitLifeApplication

        lifecycleScope.launch {
            try {
                // Create user with Firebase Auth
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    // Update display name in Firebase
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    firebaseUser.updateProfile(profileUpdates).await()

                    // Create local user record (Room database for offline storage)
                    val result = app.userRepository.register(email, password, name)

                    result.fold(
                        onSuccess = { user ->
                            sessionManager.saveUserSession(user.id, user.email, user.name)
                            showLoading(false)
                            Toast.makeText(context, getString(R.string.welcome_user, user.name), Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_RegisterFragment_to_HomeFragment)
                        },
                        onFailure = { exception ->
                            showLoading(false)
                            Toast.makeText(context, exception.message ?: getString(R.string.error_registration_failed), Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    showLoading(false)
                    Toast.makeText(context, getString(R.string.error_registration_failed), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true -> "An account with this email already exists"
                    e.message?.contains("email address is badly formatted") == true -> "Please enter a valid email address"
                    e.message?.contains("weak password") == true -> "Password is too weak. Please use at least 6 characters"
                    e.message?.contains("network") == true -> "Network error. Please check your connection"
                    else -> e.message ?: getString(R.string.error_registration_failed)
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.etName.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.etConfirmPassword.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
