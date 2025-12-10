package com.example.fitlife.ui.profile

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.utils.ImageUploadService
import com.example.fitlife.utils.PermissionManager
import com.example.fitlife.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {
    
    companion object {
        private const val TAG = "ProfileFragment"
    }

    private lateinit var tvAvatarInitials: TextView
    private lateinit var ivProfilePhoto: ImageView
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
    private var currentPhotoFile: File? = null  // Store actual file for proper cleanup
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
            currentPhotoUri?.let { cacheUri ->
                // Copy from cache to internal storage for persistence
                val persistentUri = copyImageToInternalStorage(cacheUri)
                if (persistentUri != null) {
                    profilePhotoUri = persistentUri
                    saveAndDisplayProfilePhoto(persistentUri)
                    
                    // Clean up the temporary cache file using the stored File reference
                    currentPhotoFile?.let { file ->
                        try {
                            if (file.exists()) {
                                val deleted = file.delete()
                                if (deleted) {
                                    Log.d(TAG, "Deleted temporary camera file: ${file.name}")
                                } else {
                                    Log.w(TAG, "Failed to delete temporary camera file: ${file.name}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting temporary camera file: ${e.message}", e)
                        }
                    }
                } else {
                    // Failed to copy, show error
                    context?.let { ctx ->
                        Toast.makeText(ctx, getString(R.string.error_saving_photo), Toast.LENGTH_SHORT).show()
                    }
                }
                currentPhotoUri = null
                currentPhotoFile = null
            }
        } else {
            // Camera was cancelled, clean up the temp file
            currentPhotoFile?.let { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up cancelled camera file: ${e.message}", e)
                }
            }
            currentPhotoUri = null
            currentPhotoFile = null
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Copy to internal storage and save
            val savedUri = copyImageToInternalStorage(it)
            if (savedUri != null) {
                profilePhotoUri = savedUri
                saveAndDisplayProfilePhoto(savedUri)
            }
        }
    }
    
    private fun saveAndDisplayProfilePhoto(uri: Uri) {
        // Display the photo immediately using Glide (async, off UI thread)
        if (isAdded) {
            ivProfilePhoto.visibility = View.VISIBLE
            tvAvatarInitials.visibility = View.GONE
            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(ivProfilePhoto)
        }
        
        // Upload to cloud and save URL to database
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) {
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Uploading image...", Toast.LENGTH_SHORT).show()
                }
                
                // Upload to FreeImage.host
                val uploadResult = ImageUploadService.uploadImage(requireContext(), uri)
                
                uploadResult.onSuccess { imageUrl ->
                    // Save the cloud URL to the database
                    viewLifecycleOwner.lifecycleScope.launch {
                        val user = userRepository.getUserById(userId)
                        if (user != null) {
                            val updatedUser = user.copy(profilePhotoUri = imageUrl)
                            userRepository.updateUser(updatedUser)
                            context?.let { ctx ->
                                Toast.makeText(ctx, getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()
                            }
                            
                            // Load from cloud URL using Glide
                            if (isAdded) {
                                Glide.with(requireContext())
                                    .load(imageUrl)
                                    .circleCrop()
                                    .into(ivProfilePhoto)
                            }
                        } else {
                            context?.let { ctx ->
                                Toast.makeText(ctx, getString(R.string.error_user_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Cloud upload failed: ${error.message}", error)
                    // Fallback: save the content URI string if cloud upload fails
                    // The content:// URI will work with Glide and is safe on API 24+
                    val uriString = uri.toString()
                    val user = userRepository.getUserById(userId)
                    if (user != null) {
                        val updatedUser = user.copy(profilePhotoUri = uriString)
                        userRepository.updateUser(updatedUser)
                        context?.let { ctx ->
                            Toast.makeText(ctx, "Cloud upload failed: ${error.message}. Saved locally.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        context?.let { ctx ->
                            Toast.makeText(ctx, getString(R.string.error_user_not_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving photo: ${e.message}", e)
                context?.let { ctx ->
                    Toast.makeText(ctx, "Error saving photo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val ctx = context ?: return null
            
            // Delete previous profile photos to prevent buildup
            deleteOldProfilePhotos()
            
            val inputStream = ctx.contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $sourceUri")
                return null
            }
            
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(ctx.filesDir, "profile_photo_$timeStamp.jpg")
            
            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Return a content:// URI via FileProvider to avoid FileUriExposedException on API 24+
            FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image to internal storage: ${e.message}", e)
            null
        }
    }
    
    /**
     * Deletes old profile photo files to prevent storage buildup.
     * Only deletes files matching the profile_photo_*.jpg pattern.
     */
    private fun deleteOldProfilePhotos() {
        try {
            val ctx = context ?: return
            val filesDir = ctx.filesDir
            val profilePhotos = filesDir.listFiles { file ->
                file.name.startsWith("profile_photo_") && file.name.endsWith(".jpg")
            }
            
            profilePhotos?.forEach { file ->
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted old profile photo: ${file.name}")
                } else {
                    Log.w(TAG, "Failed to delete old profile photo: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old profile photos: ${e.message}", e)
        }
    }
    
    private fun loadAndDisplayProfilePhoto(photoUriString: String?) {
        if (!photoUriString.isNullOrEmpty() && isAdded) {
            try {
                ivProfilePhoto.visibility = View.VISIBLE
                tvAvatarInitials.visibility = View.GONE
                
                // Determine the source type and load with Glide (async, off UI thread)
                val loadSource: Any = when {
                    // Cloud URL
                    photoUriString.startsWith("http://") || photoUriString.startsWith("https://") -> photoUriString
                    // File URI (file://...)
                    photoUriString.startsWith("file://") -> Uri.parse(photoUriString)
                    // Content URI (content://...)
                    photoUriString.startsWith("content://") -> Uri.parse(photoUriString)
                    // Raw file path (e.g., /data/...)
                    photoUriString.startsWith("/") -> File(photoUriString)
                    // Fallback: try as URI
                    else -> Uri.parse(photoUriString)
                }
                
                Glide.with(requireContext())
                    .load(loadSource)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(ivProfilePhoto)
            } catch (e: Exception) {
                // Fallback to initials
                ivProfilePhoto.visibility = View.GONE
                tvAvatarInitials.visibility = View.VISIBLE
            }
        } else {
            ivProfilePhoto.visibility = View.GONE
            tvAvatarInitials.visibility = View.VISIBLE
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
        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto)
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
        
        // Profile photo click to change photo
        ivProfilePhoto.setOnClickListener {
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
            currentPhotoFile = photoFile  // Store file reference for cleanup
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
                
                // Load profile photo if exists
                loadAndDisplayProfilePhoto(currentUser.profilePhotoUri)

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
