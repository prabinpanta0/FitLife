package com.example.fitlife.ui.routines

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.*
import com.example.fitlife.databinding.FragmentAddRoutineBinding
import com.example.fitlife.databinding.DialogAddExerciseBinding
import com.example.fitlife.ui.adapters.ExerciseAdapter
import com.example.fitlife.utils.SessionManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AddRoutineFragment : Fragment() {

    private var _binding: FragmentAddRoutineBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var exerciseAdapter: ExerciseAdapter

    private var routineId: Long = -1
    private var selectedDayOfWeek: Int = -1
    private var selectedLocationId: Long? = null
    private var exercises = mutableListOf<Exercise>()
    private var exerciseEquipment = mutableMapOf<Int, MutableList<String>>() // temp index -> equipment names
    private var locations = listOf<GeoLocation>()

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
                    selectedDayOfWeek = dayValue
                } else if (selectedDayOfWeek == dayValue) {
                    selectedDayOfWeek = -1
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

                // Set day chip
                selectedDayOfWeek = data.routine.dayOfWeek
                setDayChipChecked(data.routine.dayOfWeek)

                // Set location
                selectedLocationId = data.routine.locationId

                // Load exercises
                exercises.clear()
                exercises.addAll(data.exercises)
                updateExercisesList()
            }
        }
    }

    private fun setDayChipChecked(dayOfWeek: Int) {
        when (dayOfWeek) {
            0 -> binding.chipSun.isChecked = true
            1 -> binding.chipMon.isChecked = true
            2 -> binding.chipTue.isChecked = true
            3 -> binding.chipWed.isChecked = true
            4 -> binding.chipThu.isChecked = true
            5 -> binding.chipFri.isChecked = true
            6 -> binding.chipSat.isChecked = true
        }
    }

    private fun showAddExerciseDialog() {
        val dialogBinding = DialogAddExerciseBinding.inflate(layoutInflater)
        var selectedEmoji = "💪"
        val equipmentList = mutableListOf<String>()

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
                        orderIndex = exercises.size
                    )
                    exercises.add(exercise)
                    exerciseEquipment[exercises.size - 1] = equipmentList
                    updateExercisesList()
                }
            }
            .show()
    }

    private fun showEditExerciseDialog(exercise: Exercise) {
        val dialogBinding = DialogAddExerciseBinding.inflate(layoutInflater)
        var selectedEmoji = exercise.imageEmoji
        val equipmentList = mutableListOf<String>()

        // Pre-fill data
        dialogBinding.etExerciseName.setText(exercise.name)
        dialogBinding.etSets.setText(exercise.sets.toString())
        dialogBinding.etReps.setText(exercise.reps.toString())
        dialogBinding.etInstructions.setText(exercise.instructions)

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
                            imageEmoji = selectedEmoji
                        )
                        exerciseEquipment[index] = equipmentList
                        updateExercisesList()
                    }
                }
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
                val routine = WorkoutRoutine(
                    id = if (routineId != -1L) routineId else 0,
                    userId = userId,
                    name = name,
                    description = description,
                    dayOfWeek = selectedDayOfWeek,
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
