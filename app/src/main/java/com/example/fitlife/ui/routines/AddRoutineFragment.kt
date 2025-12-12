package com.example.fitlife.ui.routines

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.*
import com.example.fitlife.databinding.FragmentAddRoutineBinding
import com.example.fitlife.databinding.DialogAddExerciseBinding
import com.example.fitlife.ui.adapters.ExerciseAdapter
import com.example.fitlife.utils.PermissionManager
import com.example.fitlife.utils.SessionManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddRoutineFragment : Fragment() {
    
    companion object {
        // Prefix to identify relative paths stored in the database
        // This distinguishes relative paths from absolute paths or URIs
        private const val RELATIVE_PATH_PREFIX = "@filesDir/"
    }

    private var _binding: FragmentAddRoutineBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var exerciseAdapter: ExerciseAdapter

    private var routineId: Long = -1
    private var selectedDaysOfWeek = mutableSetOf<Int>() // Changed to support multiple days
    private var selectedLocationId: Long? = null
    private var exercises = mutableListOf<Exercise>()
    private var exerciseEquipment = mutableMapOf<Int, MutableList<String>>() // temp index -> equipment names
    private var locations = listOf<GeoLocation>()
    
    // Exercise image handling
    private var currentExercisePhotoUri: Uri? = null
    private var currentDialogBinding: DialogAddExerciseBinding? = null
    private var pendingImageResourceName: String? = null
    private var pendingImageUri: String? = null
    
    // Camera permission
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            launchExerciseCamera()
        } else {
            Toast.makeText(requireContext(), getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }
    
    // Take picture for exercise
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentExercisePhotoUri?.let { cacheUri ->
                // Copy from cache to internal storage for persistence
                val result = copyImageToInternalStorage(cacheUri)
                if (result != null) {
                    val (savedFile, relativePath) = result
                    // Store relative path with prefix for stable persistence
                    pendingImageUri = RELATIVE_PATH_PREFIX + relativePath
                    pendingImageResourceName = null
                    currentDialogBinding?.let { binding ->
                        // Use Glide for async image loading
                        if (isAdded) {
                            Glide.with(requireContext())
                                .load(savedFile)
                                .centerCrop()
                                .into(binding.ivExerciseImage)
                        }
                        binding.ivExerciseImage.visibility = View.VISIBLE
                        binding.llImagePlaceholder.visibility = View.GONE
                    }
                    
                    // Clean up temporary cache file
                    try {
                        val cacheFile = File(cacheUri.path ?: "")
                        if (cacheFile.exists()) {
                            cacheFile.delete()
                        }
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                    
                    context?.let { ctx ->
                        Toast.makeText(ctx, getString(R.string.photo_captured), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let { ctx ->
                        Toast.makeText(ctx, getString(R.string.error_saving_photo), Toast.LENGTH_SHORT).show()
                    }
                }
                currentExercisePhotoUri = null
            }
        }
    }
    
    // Pick image from gallery
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val result = copyImageToInternalStorage(it)
            if (result != null) {
                val (savedFile, relativePath) = result
                // Store relative path with prefix for stable persistence
                pendingImageUri = RELATIVE_PATH_PREFIX + relativePath
                pendingImageResourceName = null
                currentDialogBinding?.let { binding ->
                    // Use Glide for async image loading
                    if (isAdded) {
                        Glide.with(requireContext())
                            .load(savedFile)
                            .centerCrop()
                            .into(binding.ivExerciseImage)
                    }
                    binding.ivExerciseImage.visibility = View.VISIBLE
                    binding.llImagePlaceholder.visibility = View.GONE
                }
                Toast.makeText(requireContext(), getString(R.string.photo_selected), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkCameraPermissionAndCapture() {
        when {
            PermissionManager.hasPermissions(requireContext(), PermissionManager.CAMERA_PERMISSION) -> {
                launchExerciseCamera()
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
    
    private fun launchExerciseCamera() {
        try {
            val photoFile = createImageFile()
            currentExercisePhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            currentExercisePhotoUri?.let { takePicture.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.camera_launch_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        return File.createTempFile("EXERCISE_${timeStamp}_", ".jpg", storageDir)
    }
    
    /**
     * Copies an image to internal storage and returns both the File and relative path.
     * @return Pair of (File, relativePath) or null if copy failed
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Pair<File, String>? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(sourceUri)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "exercise_photo_$timeStamp.jpg"
            val file = File(requireContext().filesDir, fileName)
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Return both the file (for immediate display) and the relative filename (for storage)
            Pair(file, fileName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Resolves an image URI string to a source that Glide can load.
     * Handles relative paths (prefixed with @filesDir/), absolute paths, and various URI schemes.
     */
    private fun resolveImageSource(imageUri: String): Any {
        return when {
            // Relative path stored with our prefix - reconstruct full path
            imageUri.startsWith(RELATIVE_PATH_PREFIX) -> {
                val relativePath = imageUri.removePrefix(RELATIVE_PATH_PREFIX)
                File(requireContext().filesDir, relativePath)
            }
            // Cloud URL
            imageUri.startsWith("http://") || imageUri.startsWith("https://") -> imageUri
            // File URI
            imageUri.startsWith("file://") -> Uri.parse(imageUri)
            // Content URI
            imageUri.startsWith("content://") -> Uri.parse(imageUri)
            // Legacy absolute path (e.g., /data/...) - try to load directly
            imageUri.startsWith("/") -> File(imageUri)
            // Fallback: try as URI
            else -> Uri.parse(imageUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        routineId = arguments?.getLong("routineId", -1) ?: -1

        setupRecyclerView()
        setupDayChips()
        setupClickListeners()
        loadLocations()

        if (routineId != -1L) {
            binding.tvTitle.text = getString(R.string.edit_routine)
            loadExistingRoutine()
        }
    }

    private fun setupRecyclerView() {
        exerciseAdapter = ExerciseAdapter(
            onExerciseClick = { exercise ->
                showEditExerciseDialog(exercise)
            },
            onDeleteClick = { exercise ->
                exercises.remove(exercise)
                updateExercisesList()
            },
            onCompletionToggle = { exercise, isCompleted ->
                val index = exercises.indexOfFirst { it.id == exercise.id || it.name == exercise.name }
                if (index >= 0) {
                    exercises[index] = exercise.copy(isCompleted = isCompleted)
                }
            }
        )

        binding.rvExercises.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = exerciseAdapter
        }
    }

    private fun setupDayChips() {
        val chips = listOf(
            binding.chipSun to 0,
            binding.chipMon to 1,
            binding.chipTue to 2,
            binding.chipWed to 3,
            binding.chipThu to 4,
            binding.chipFri to 5,
            binding.chipSat to 6
        )

        chips.forEach { (chip, dayValue) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDaysOfWeek.add(dayValue)
                } else {
                    selectedDaysOfWeek.remove(dayValue)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnAddExercise.setOnClickListener {
            showAddExerciseDialog()
        }

        binding.btnSaveRoutine.setOnClickListener {
            saveRoutine()
        }

        binding.actvLocation.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                selectedLocationId = null
            } else {
                selectedLocationId = locations.getOrNull(position - 1)?.id
            }
        }
    }

    private fun loadLocations() {
        val app = requireActivity().application as FitLifeApplication
        val userId = sessionManager.getUserId()

        lifecycleScope.launch {
            app.locationRepository.getLocationsByUserId(userId).collect { locationsList ->
                locations = locationsList

                val locationNames = mutableListOf("None")
                locationNames.addAll(locationsList.map { "${it.locationType.emoji} ${it.name}" })

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    locationNames
                )
                binding.actvLocation.setAdapter(adapter)
                
                // Update dropdown display if editing an existing routine
                if (routineId != -1L && selectedLocationId != null) {
                    updateLocationDropdown()
                }
            }
        }
    }

    private fun loadExistingRoutine() {
        val app = requireActivity().application as FitLifeApplication

        lifecycleScope.launch {
            val routineWithExercises = app.workoutRepository.getRoutineWithExercises(routineId)

            routineWithExercises?.let { data ->
                binding.etRoutineName.setText(data.routine.name)
                binding.etDescription.setText(data.routine.description)

                // Set day chips (supports multiple days)
                selectedDaysOfWeek.clear()
                selectedDaysOfWeek.addAll(data.routine.getDaysAsList())
                setDayChipsChecked(data.routine.getDaysAsList())

                // Set location and display it in the dropdown
                selectedLocationId = data.routine.locationId
                if (selectedLocationId != null && locations.isNotEmpty()) {
                    updateLocationDropdown()
                }

                // Load exercises
                exercises.clear()
                exercises.addAll(data.exercises)
                updateExercisesList()
            }
        }
    }

    private fun updateLocationDropdown() {
        selectedLocationId?.let { locId ->
            val location = locations.find { it.id == locId }
            location?.let {
                binding.actvLocation.setText("${it.locationType.emoji} ${it.name}", false)
            }
        } ?: run {
            binding.actvLocation.setText("None", false)
        }
    }

    private fun setDayChipsChecked(daysOfWeek: List<Int>) {
        binding.chipSun.isChecked = daysOfWeek.contains(0)
        binding.chipMon.isChecked = daysOfWeek.contains(1)
        binding.chipTue.isChecked = daysOfWeek.contains(2)
        binding.chipWed.isChecked = daysOfWeek.contains(3)
        binding.chipThu.isChecked = daysOfWeek.contains(4)
        binding.chipFri.isChecked = daysOfWeek.contains(5)
        binding.chipSat.isChecked = daysOfWeek.contains(6)
    }

    private fun showAddExerciseDialog() {
        val dialogBinding = DialogAddExerciseBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        var selectedEmoji = "💪"
        val equipmentList = mutableListOf<String>()
        
        // Reset image state
        pendingImageUri = null
        pendingImageResourceName = null
        
        // Setup image capture buttons
        dialogBinding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndCapture()
        }
        
        dialogBinding.btnPickImage.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        // Setup preset image selection
        dialogBinding.cardPreset1.setOnClickListener {
            pendingImageResourceName = "ic_exercise_preset_workout"
            pendingImageUri = null
            dialogBinding.ivExerciseImage.setImageResource(R.drawable.ic_exercise_preset_workout)
            dialogBinding.ivExerciseImage.visibility = View.VISIBLE
            dialogBinding.llImagePlaceholder.visibility = View.GONE
            // Highlight selected preset
            dialogBinding.cardPreset1.strokeColor = requireContext().getColor(R.color.primary)
            dialogBinding.cardPreset2.strokeColor = android.graphics.Color.TRANSPARENT
        }
        
        dialogBinding.cardPreset2.setOnClickListener {
            pendingImageResourceName = "ic_exercise_preset_person"
            pendingImageUri = null
            dialogBinding.ivExerciseImage.setImageResource(R.drawable.ic_exercise_preset_person)
            dialogBinding.ivExerciseImage.visibility = View.VISIBLE
            dialogBinding.llImagePlaceholder.visibility = View.GONE
            // Highlight selected preset
            dialogBinding.cardPreset2.strokeColor = requireContext().getColor(R.color.primary)
            dialogBinding.cardPreset1.strokeColor = android.graphics.Color.TRANSPARENT
        }

        // Setup emoji selection
        val emojiViews = listOf(
            dialogBinding.emoji1, dialogBinding.emoji2, dialogBinding.emoji3,
            dialogBinding.emoji4, dialogBinding.emoji5, dialogBinding.emoji6,
            dialogBinding.emoji7, dialogBinding.emoji8
        )

        emojiViews.forEach { emojiView ->
            emojiView.setOnClickListener {
                selectedEmoji = emojiView.text.toString()
                emojiViews.forEach { it.alpha = 0.5f }
                emojiView.alpha = 1.0f
            }
        }

        // Setup equipment adding
        dialogBinding.tilEquipment.setEndIconOnClickListener {
            val equipment = dialogBinding.etEquipment.text.toString().trim()
            if (equipment.isNotEmpty()) {
                equipmentList.add(equipment)
                addEquipmentChip(dialogBinding, equipment, equipmentList)
                dialogBinding.etEquipment.text?.clear()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_exercise)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etExerciseName.text.toString().trim()
                val sets = dialogBinding.etSets.text.toString().toIntOrNull() ?: 3
                val reps = dialogBinding.etReps.text.toString().toIntOrNull() ?: 10
                val instructions = dialogBinding.etInstructions.text.toString().trim()

                if (name.isNotEmpty()) {
                    val exercise = Exercise(
                        id = 0,
                        routineId = routineId,
                        name = name,
                        sets = sets,
                        reps = reps,
                        instructions = instructions,
                        imageEmoji = selectedEmoji,
                        imageResourceName = pendingImageResourceName,
                        imageUri = pendingImageUri,
                        orderIndex = exercises.size
                    )
                    exercises.add(exercise)
                    exerciseEquipment[exercises.size - 1] = equipmentList
                    updateExercisesList()
                }
            }
            .setOnDismissListener {
                currentDialogBinding = null
            }
            .show()
    }

    private fun showEditExerciseDialog(exercise: Exercise) {
        val dialogBinding = DialogAddExerciseBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        var selectedEmoji = exercise.imageEmoji
        val equipmentList = mutableListOf<String>()
        
        // Initialize image state from existing exercise
        pendingImageUri = exercise.imageUri
        pendingImageResourceName = exercise.imageResourceName

        // Pre-fill data
        dialogBinding.etExerciseName.setText(exercise.name)
        dialogBinding.etSets.setText(exercise.sets.toString())
        dialogBinding.etReps.setText(exercise.reps.toString())
        dialogBinding.etInstructions.setText(exercise.instructions)
        
        // Load existing image if present
        exercise.imageUri?.takeIf { it.isNotEmpty() }?.let { uri ->
            try {
                // Use helper to resolve the image source
                val loadSource = resolveImageSource(uri)
                
                if (isAdded) {
                    Glide.with(requireContext())
                        .load(loadSource)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image)
                        .error(R.drawable.ic_image)
                        .into(dialogBinding.ivExerciseImage)
                }
                dialogBinding.ivExerciseImage.visibility = View.VISIBLE
                dialogBinding.llImagePlaceholder.visibility = View.GONE
            } catch (e: Exception) {
                // Fallback - keep placeholder visible
            }
        } ?: exercise.imageResourceName?.takeIf { it.isNotEmpty() }?.let { resourceName ->
            val resId = requireContext().resources.getIdentifier(
                resourceName, "drawable", requireContext().packageName
            )
            if (resId != 0) {
                dialogBinding.ivExerciseImage.setImageResource(resId)
                dialogBinding.ivExerciseImage.visibility = View.VISIBLE
                dialogBinding.llImagePlaceholder.visibility = View.GONE
                // Highlight the preset
                when (resourceName) {
                    "ic_exercise_preset_workout" -> {
                        dialogBinding.cardPreset1.strokeColor = requireContext().getColor(R.color.primary)
                    }
                    "ic_exercise_preset_person" -> {
                        dialogBinding.cardPreset2.strokeColor = requireContext().getColor(R.color.primary)
                    }
                }
            }
        }
        
        // Setup image capture buttons
        dialogBinding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndCapture()
        }
        
        dialogBinding.btnPickImage.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        // Setup preset image selection
        dialogBinding.cardPreset1.setOnClickListener {
            pendingImageResourceName = "ic_exercise_preset_workout"
            pendingImageUri = null
            dialogBinding.ivExerciseImage.setImageResource(R.drawable.ic_exercise_preset_workout)
            dialogBinding.ivExerciseImage.visibility = View.VISIBLE
            dialogBinding.llImagePlaceholder.visibility = View.GONE
            dialogBinding.cardPreset1.strokeColor = requireContext().getColor(R.color.primary)
            dialogBinding.cardPreset2.strokeColor = android.graphics.Color.TRANSPARENT
        }
        
        dialogBinding.cardPreset2.setOnClickListener {
            pendingImageResourceName = "ic_exercise_preset_person"
            pendingImageUri = null
            dialogBinding.ivExerciseImage.setImageResource(R.drawable.ic_exercise_preset_person)
            dialogBinding.ivExerciseImage.visibility = View.VISIBLE
            dialogBinding.llImagePlaceholder.visibility = View.GONE
            dialogBinding.cardPreset2.strokeColor = requireContext().getColor(R.color.primary)
            dialogBinding.cardPreset1.strokeColor = android.graphics.Color.TRANSPARENT
        }

        // Setup emoji selection
        val emojiViews = listOf(
            dialogBinding.emoji1, dialogBinding.emoji2, dialogBinding.emoji3,
            dialogBinding.emoji4, dialogBinding.emoji5, dialogBinding.emoji6,
            dialogBinding.emoji7, dialogBinding.emoji8
        )

        emojiViews.forEach { emojiView ->
            if (emojiView.text.toString() == selectedEmoji) {
                emojiView.alpha = 1.0f
            } else {
                emojiView.alpha = 0.5f
            }
            emojiView.setOnClickListener {
                selectedEmoji = emojiView.text.toString()
                emojiViews.forEach { it.alpha = 0.5f }
                emojiView.alpha = 1.0f
            }
        }

        // Setup equipment adding
        dialogBinding.tilEquipment.setEndIconOnClickListener {
            val equipment = dialogBinding.etEquipment.text.toString().trim()
            if (equipment.isNotEmpty()) {
                equipmentList.add(equipment)
                addEquipmentChip(dialogBinding, equipment, equipmentList)
                dialogBinding.etEquipment.text?.clear()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_exercise)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etExerciseName.text.toString().trim()
                val sets = dialogBinding.etSets.text.toString().toIntOrNull() ?: 3
                val reps = dialogBinding.etReps.text.toString().toIntOrNull() ?: 10
                val instructions = dialogBinding.etInstructions.text.toString().trim()

                if (name.isNotEmpty()) {
                    val index = exercises.indexOf(exercise)
                    if (index >= 0) {
                        exercises[index] = exercise.copy(
                            name = name,
                            sets = sets,
                            reps = reps,
                            instructions = instructions,
                            imageEmoji = selectedEmoji,
                            imageResourceName = pendingImageResourceName,
                            imageUri = pendingImageUri
                        )
                        exerciseEquipment[index] = equipmentList
                        updateExercisesList()
                    }
                }
            }
            .setOnDismissListener {
                currentDialogBinding = null
            }
            .show()
    }

    private fun addEquipmentChip(dialogBinding: DialogAddExerciseBinding, name: String, list: MutableList<String>) {
        val chip = Chip(requireContext()).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                list.remove(name)
                dialogBinding.chipGroupEquipment.removeView(this)
            }
        }
        dialogBinding.chipGroupEquipment.addView(chip)
    }

    private fun updateExercisesList() {
        exerciseAdapter.submitList(exercises.toList())
        binding.tvNoExercises.visibility = if (exercises.isEmpty()) View.VISIBLE else View.GONE
        binding.rvExercises.visibility = if (exercises.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun saveRoutine() {
        val name = binding.etRoutineName.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilRoutineName.error = getString(R.string.error_empty_field)
            return
        }
        binding.tilRoutineName.error = null

        val app = requireActivity().application as FitLifeApplication
        val userId = sessionManager.getUserId()

        lifecycleScope.launch {
            try {
                // Convert selected days set to sorted list for storage
                val daysOfWeekString = WorkoutRoutine.daysListToString(selectedDaysOfWeek.toList())
                // Use first selected day for backward compatibility, or -1 if none selected
                val primaryDay = selectedDaysOfWeek.minOrNull() ?: -1
                
                val routine = WorkoutRoutine(
                    id = if (routineId != -1L) routineId else 0,
                    userId = userId,
                    name = name,
                    description = description,
                    dayOfWeek = primaryDay,
                    daysOfWeek = daysOfWeekString,
                    locationId = selectedLocationId,
                    updatedAt = System.currentTimeMillis()
                )

                val savedRoutineId = if (routineId != -1L) {
                    app.workoutRepository.updateRoutine(routine)
                    routineId
                } else {
                    app.workoutRepository.insertRoutine(routine)
                }

                // Delete existing exercises if editing
                if (routineId != -1L) {
                    app.workoutRepository.deleteExercisesByRoutineId(routineId)
                }

                // Save exercises
                exercises.forEachIndexed { index, exercise ->
                    val savedExercise = exercise.copy(
                        id = 0,
                        routineId = savedRoutineId,
                        orderIndex = index
                    )
                    val exerciseId = app.workoutRepository.insertExercise(savedExercise)

                    // Save equipment for this exercise
                    exerciseEquipment[index]?.forEach { equipmentName ->
                        val equipment = Equipment(
                            exerciseId = exerciseId,
                            name = equipmentName,
                            category = guessEquipmentCategory(equipmentName)
                        )
                        app.workoutRepository.insertEquipment(equipment)
                    }
                }

                Toast.makeText(context, R.string.routine_saved, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()

            } catch (e: Exception) {
                Toast.makeText(context, R.string.error_generic, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun guessEquipmentCategory(name: String): EquipmentCategory {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("dumbbell") || lowerName.contains("barbell") ||
            lowerName.contains("weight") || lowerName.contains("kettlebell") -> EquipmentCategory.WEIGHTS
            lowerName.contains("band") || lowerName.contains("resistance") -> EquipmentCategory.RESISTANCE
            lowerName.contains("mat") || lowerName.contains("floor") -> EquipmentCategory.MATS
            lowerName.contains("treadmill") || lowerName.contains("bike") ||
            lowerName.contains("rowing") || lowerName.contains("elliptical") -> EquipmentCategory.CARDIO
            lowerName.contains("bench") || lowerName.contains("rack") ||
            lowerName.contains("machine") || lowerName.contains("cable") -> EquipmentCategory.STRENGTH
            lowerName.contains("gloves") || lowerName.contains("strap") ||
            lowerName.contains("belt") || lowerName.contains("rope") -> EquipmentCategory.ACCESSORIES
            else -> EquipmentCategory.OTHER
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
