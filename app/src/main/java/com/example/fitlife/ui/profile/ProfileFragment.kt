package com.example.fitlife.ui.profile

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.utils.PermissionManager
import com.example.fitlife.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var tvAvatarInitials: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvTotalWorkouts: TextView
    private lateinit var tvTotalRoutines: TextView
    private lateinit var tvActiveStreak: TextView
    private lateinit var llEditProfile: LinearLayout
    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var switchDarkMode: SwitchMaterial
    private lateinit var llAbout: LinearLayout
    private lateinit var btnLogout: MaterialButton

    private lateinit var sessionManager: SessionManager

    // Photo capture variables
    private var currentPhotoUri: Uri? = null
    private var profilePhotoUri: Uri? = null

    private val userRepository by lazy {
        (requireActivity().application as FitLifeApplication).userRepository
    }

    private val workoutRepository by lazy {
        (requireActivity().application as FitLifeApplication).workoutRepository
    }

    // Permission request launchers
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            launchCamera()
        } else {
            handleCameraPermissionDenied()
        }
    }

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            launchGallery()
        } else {
            handleStoragePermissionDenied()
        }
    }

    // Activity result launchers
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                profilePhotoUri = uri
                Toast.makeText(requireContext(), getString(R.string.photo_captured), Toast.LENGTH_SHORT).show()
                // In a real app, save this URI to user profile and display it
            }
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            profilePhotoUri = it
            Toast.makeText(requireContext(), getString(R.string.photo_selected), Toast.LENGTH_SHORT).show()
            // In a real app, save this URI to user profile and display it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        initViews(view)
        setupClickListeners()
        loadUserData()
        loadStats()
    }

    private fun initViews(view: View) {
        tvAvatarInitials = view.findViewById(R.id.tvAvatarInitials)
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserEmail = view.findViewById(R.id.tvUserEmail)
        tvMemberSince = view.findViewById(R.id.tvMemberSince)
        tvTotalWorkouts = view.findViewById(R.id.tvTotalWorkouts)
        tvTotalRoutines = view.findViewById(R.id.tvTotalRoutines)
        tvActiveStreak = view.findViewById(R.id.tvActiveStreak)
        llEditProfile = view.findViewById(R.id.llEditProfile)
        switchNotifications = view.findViewById(R.id.switchNotifications)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        llAbout = view.findViewById(R.id.llAbout)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Load dark mode preference
        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        switchDarkMode.isChecked = isDarkMode
    }

    private fun setupClickListeners() {
        llEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        // Avatar click to change photo
        tvAvatarInitials.setOnClickListener {
            showPhotoOptionsDialog()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkNotificationPermission()
            } else {
                Toast.makeText(requireContext(), getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        llAbout.setOnClickListener {
            showAboutDialog()
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showPhotoOptionsDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndCapture()
                    1 -> checkStoragePermissionAndPick()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndCapture() {
        when {
            PermissionManager.hasPermissions(requireContext(), PermissionManager.CAMERA_PERMISSION) -> {
                launchCamera()
            }
            PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.CAMERA_PERMISSION) -> {
                PermissionManager.showRationaleDialog(
                    context = requireContext(),
                    title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.CAMERA),
                    message = PermissionManager.getRationaleMessage(PermissionManager.PermissionType.CAMERA),
                    onPositiveClick = {
                        requestCameraPermission.launch(PermissionManager.CAMERA_PERMISSION)
                    }
                )
            }
            else -> {
                requestCameraPermission.launch(PermissionManager.CAMERA_PERMISSION)
            }
        }
    }

    private fun checkStoragePermissionAndPick() {
        if (PermissionManager.STORAGE_PERMISSIONS.isEmpty() ||
            PermissionManager.hasPermissions(requireContext(), PermissionManager.STORAGE_PERMISSIONS)) {
            launchGallery()
        } else if (PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.STORAGE_PERMISSIONS)) {
            PermissionManager.showRationaleDialog(
                context = requireContext(),
                title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.STORAGE),
                message = PermissionManager.getRationaleMessage(PermissionManager.PermissionType.STORAGE),
                onPositiveClick = {
                    requestStoragePermission.launch(PermissionManager.STORAGE_PERMISSIONS)
                }
            )
        } else {
            requestStoragePermission.launch(PermissionManager.STORAGE_PERMISSIONS)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionManager.hasPermissions(requireContext(), PermissionManager.NOTIFICATION_PERMISSION)) {
                PermissionManager.showRationaleDialog(
                    context = requireContext(),
                    title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.NOTIFICATION),
                    message = PermissionManager.getRationaleMessage(PermissionManager.PermissionType.NOTIFICATION),
                    onPositiveClick = {
                        // Request notification permission
                        Toast.makeText(requireContext(), getString(R.string.enable_notifications_settings), Toast.LENGTH_SHORT).show()
                    },
                    onNegativeClick = {
                        switchNotifications.isChecked = false
                    }
                )
            } else {
                Toast.makeText(requireContext(), getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            currentPhotoUri?.let { takePicture.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.camera_launch_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGallery() {
        pickImage.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun handleCameraPermissionDenied() {
        if (!PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.CAMERA_PERMISSION)) {
            // Permission permanently denied
            PermissionManager.showSettingsDialog(
                context = requireContext(),
                title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.CAMERA),
                message = PermissionManager.getSettingsMessage(PermissionManager.PermissionType.CAMERA)
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleStoragePermissionDenied() {
        if (!PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.STORAGE_PERMISSIONS)) {
            PermissionManager.showSettingsDialog(
                context = requireContext(),
                title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.STORAGE),
                message = PermissionManager.getSettingsMessage(PermissionManager.PermissionType.STORAGE)
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.storage_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) {
            navigateToLogin()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userRepository.getUserById(userId)
            user?.let { currentUser ->
                tvUserName.text = currentUser.name
                tvUserEmail.text = currentUser.email

                // Set avatar initials
                val initials = currentUser.name.split(" ")
                    .take(2)
                    .mapNotNull { part -> part.firstOrNull()?.uppercase() }
                    .joinToString("")
                tvAvatarInitials.text = initials.ifEmpty { "U" }

                // Format member since date
                val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                tvMemberSince.text = "Member since ${dateFormat.format(currentUser.createdAt)}"
            }
        }
    }

    private fun loadStats() {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) return

        viewLifecycleOwner.lifecycleScope.launch {
            // Load total routines with exercises
            workoutRepository.getRoutinesWithExercises(userId).first().let { routines ->
                tvTotalRoutines.text = routines.size.toString()

                // Count completed exercises as "workouts"
                val completedCount = routines.sumOf { routineWithExercises ->
                    routineWithExercises.exercises.count { exercise -> exercise.isCompleted }
                }
                tvTotalWorkouts.text = completedCount.toString()
            }

            // Streak calculation would need more complex logic with dates
            // For now, show a simple count
            tvActiveStreak.text = "0"
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_profile, null)

        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)

        // Pre-fill current values
        etName.setText(tvUserName.text)
        etEmail.setText(tvUserEmail.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_profile))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = etName.text?.toString()?.trim() ?: ""
                val newEmail = etEmail.text?.toString()?.trim() ?: ""

                if (newName.isEmpty() || newEmail.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.name_email_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateProfile(newName, newEmail)
            }
            .show()
    }

    private fun updateProfile(name: String, email: String) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) return

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userRepository.getUserById(userId)
            user?.let { currentUser ->
                val updatedUser = currentUser.copy(name = name, email = email)
                userRepository.updateUser(updatedUser)
                Toast.makeText(requireContext(), getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                loadUserData() // Refresh UI
            }
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.confirm_logout))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                logout()
            }
            .show()
    }

    private fun logout() {
        // Sign out from Firebase
        Firebase.auth.signOut()
        // Clear local session
        sessionManager.logout()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        findNavController().navigate(R.id.action_profile_to_login)
    }
}
