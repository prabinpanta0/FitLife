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
import com.example.fitlife.databinding.FragmentLoginBinding
import com.example.fitlife.utils.SessionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        firebaseAuth = Firebase.auth

        // Check if already logged in (Firebase + local session)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && sessionManager.isLoggedIn()) {
            navigateToHome()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_LoginFragment_to_RegisterFragment)
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

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

        return isValid
    }

    private fun performLogin(email: String, password: String) {
        showLoading(true)

        val app = requireActivity().application as FitLifeApplication

        lifecycleScope.launch {
            try {
                // Sign in with Firebase Auth
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    // Check if user exists in local database, if not create them
                    var localUser = app.userRepository.getUserByEmail(email)

                    if (localUser == null) {
                        // Create local user record for existing Firebase user
                        val result = app.userRepository.register(
                            email = email,
                            password = password,
                            name = firebaseUser.displayName ?: email.substringBefore("@")
                        )
                        localUser = result.getOrNull()
                    }

                    if (localUser != null) {
                        sessionManager.saveUserSession(localUser.id, localUser.email, localUser.name)
                        showLoading(false)
                        Toast.makeText(context, getString(R.string.welcome_back_user, localUser.name), Toast.LENGTH_SHORT).show()
                        navigateToHome()
                    } else {
                        showLoading(false)
                        Toast.makeText(context, getString(R.string.error_login_failed), Toast.LENGTH_LONG).show()
                    }
                } else {
                    showLoading(false)
                    Toast.makeText(context, getString(R.string.error_login_failed), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                val errorMessage = when {
                    e.message?.contains("no user record") == true -> "No account found with this email"
                    e.message?.contains("password is invalid") == true -> "Incorrect password"
                    e.message?.contains("network") == true -> "Network error. Please check your connection"
                    else -> e.message ?: getString(R.string.error_login_failed)
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun navigateToHome() {
        // Only navigate if we're currently at LoginFragment to avoid crash
        // when navigation state is already at HomeFragment
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.LoginFragment) {
            navController.navigate(R.id.action_LoginFragment_to_HomeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
