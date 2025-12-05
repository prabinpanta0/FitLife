package com.example.fitlife.ui.routines

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.RoutineWithExercises
import com.example.fitlife.databinding.FragmentRoutinesBinding
import com.example.fitlife.ui.adapters.RoutineAdapter
import com.example.fitlife.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RoutinesFragment : Fragment() {

    private var _binding: FragmentRoutinesBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var routineAdapter: RoutineAdapter

    private var allRoutines: List<RoutineWithExercises> = emptyList()
    private var selectedDayFilter: Int = -2 // -2 = All, -1 = Unscheduled, 0-6 = Days

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutinesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupChipFilters()
        setupClickListeners()
        loadRoutines()
    }

    private fun setupRecyclerView() {
        routineAdapter = RoutineAdapter(
            onRoutineClick = { routine ->
                navigateToRoutineDetail(routine)
            },
            onEditClick = { routine ->
                navigateToEditRoutine(routine)
            },
            onDeleteClick = { routine ->
                showDeleteConfirmation(routine)
            },
            onToggleComplete = { routine ->
                toggleRoutineCompletion(routine)
            }
        )

        binding.rvRoutines.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = routineAdapter
        }
    }

    private fun setupChipFilters() {
        val chipIds = listOf(
            binding.chipAll to -2,
            binding.chipSun to 0,
            binding.chipMon to 1,
            binding.chipTue to 2,
            binding.chipWed to 3,
            binding.chipThu to 4,
            binding.chipFri to 5,
            binding.chipSat to 6
        )

        chipIds.forEach { (chip, dayValue) ->
            chip.setOnClickListener {
                selectedDayFilter = dayValue
                filterRoutines()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnAddFirstRoutine.setOnClickListener {
            findNavController().navigate(R.id.AddRoutineFragment)
        }
    }

    private fun loadRoutines() {
        val app = requireActivity().application as FitLifeApplication
        val userId = sessionManager.getUserId()

        if (userId == -1L) return

        lifecycleScope.launch {
            app.workoutRepository.getRoutinesWithExercises(userId).collect { routines ->
                allRoutines = routines
                filterRoutines()
            }
        }
    }

    private fun filterRoutines() {
        val filteredRoutines = when (selectedDayFilter) {
            -2 -> allRoutines // All
            else -> allRoutines.filter { it.routine.dayOfWeek == selectedDayFilter }
        }

        routineAdapter.submitList(filteredRoutines)

        // Update empty state
        binding.llEmptyState.visibility = if (filteredRoutines.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRoutines.visibility = if (filteredRoutines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun navigateToRoutineDetail(routine: RoutineWithExercises) {
        val bundle = Bundle().apply {
            putLong("routineId", routine.routine.id)
        }
        findNavController().navigate(R.id.AddRoutineFragment, bundle)
    }

    private fun navigateToEditRoutine(routine: RoutineWithExercises) {
        val bundle = Bundle().apply {
            putLong("routineId", routine.routine.id)
        }
        findNavController().navigate(R.id.AddRoutineFragment, bundle)
    }

    private fun showDeleteConfirmation(routine: RoutineWithExercises) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_routine)
            .setMessage(R.string.confirm_delete_routine)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteRoutine(routine)
            }
            .show()
    }

    private fun deleteRoutine(routine: RoutineWithExercises) {
        val app = requireActivity().application as FitLifeApplication
        lifecycleScope.launch {
            app.workoutRepository.deleteRoutine(routine.routine)
            Toast.makeText(context, R.string.routine_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRoutineCompletion(routine: RoutineWithExercises) {
        val app = requireActivity().application as FitLifeApplication
        lifecycleScope.launch {
            app.workoutRepository.updateRoutineCompletionStatus(
                routine.routine.id,
                !routine.routine.isCompleted
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
