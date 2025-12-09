package com.example.fitlife.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.RoutineWithExercises
import com.example.fitlife.databinding.FragmentHomeBinding
import com.example.fitlife.ui.adapters.RoutineAdapter
import com.example.fitlife.utils.DateUtils
import com.example.fitlife.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var routineAdapter: RoutineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        // Check authentication
        if (!sessionManager.isLoggedIn()) {
            findNavController().navigate(R.id.action_home_to_login)
            return
        }

        setupUI()
        setupRecyclerView()
        setupClickListeners()
        loadData()
    }

    private fun setupUI() {
        // Set greeting based on time of day
        binding.tvGreeting.text = DateUtils.getGreeting()
        binding.tvUserName.text = sessionManager.getUserName() ?: "User"
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

        binding.rvUpcomingRoutines.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = routineAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnAddWorkout.setOnClickListener {
            findNavController().navigate(R.id.AddRoutineFragment)
        }

        binding.btnStartWorkout.setOnClickListener {
            // Navigate to routines or start workout
            findNavController().navigate(R.id.RoutinesFragment)
        }

        binding.cardQuickRoutines.setOnClickListener {
            findNavController().navigate(R.id.RoutinesFragment)
        }

        binding.cardQuickChecklist.setOnClickListener {
            findNavController().navigate(R.id.ChecklistFragment)
        }

        binding.cardQuickLocations.setOnClickListener {
            findNavController().navigate(R.id.MapFragment)
        }

        binding.tvViewAll.setOnClickListener {
            findNavController().navigate(R.id.RoutinesFragment)
        }
    }

    private fun loadData() {
        val app = requireActivity().application as FitLifeApplication
        val userId = sessionManager.getUserId()

        if (userId == -1L) return

        viewLifecycleOwner.lifecycleScope.launch {
            // Observe routines
            app.workoutRepository.getRoutinesWithExercises(userId).collect { routines ->
                if (_binding != null) {
                    updateUpcomingRoutines(routines)
                    updateTodayWorkout(routines)
                }
            }
        }

        // Observe progress
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                app.workoutRepository.getCompletedRoutineCount(userId),
                app.workoutRepository.getTotalScheduledRoutineCount(userId)
            ) { completed, total ->
                Pair(completed, total)
            }.collect { (completed, total) ->
                if (_binding != null) {
                    updateProgress(completed, total)
                }
            }
        }
    }

    private fun updateUpcomingRoutines(routines: List<RoutineWithExercises>) {
        val upcomingRoutines = routines
            .filter { it.routine.dayOfWeek >= 0 && !it.routine.isCompleted }
            .take(5)

        routineAdapter.submitList(upcomingRoutines)

        binding.tvNoUpcoming.visibility = if (upcomingRoutines.isEmpty()) View.VISIBLE else View.GONE
        binding.rvUpcomingRoutines.visibility = if (upcomingRoutines.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateTodayWorkout(routines: List<RoutineWithExercises>) {
        val currentDay = DateUtils.getCurrentDayOfWeek()
        val todayRoutine = routines.find { it.routine.dayOfWeek == currentDay && !it.routine.isCompleted }

        if (todayRoutine != null) {
            binding.llTodayContent.visibility = View.VISIBLE
            binding.llNoWorkout.visibility = View.GONE
            binding.tvTodayRoutineName.text = todayRoutine.routine.name
            binding.tvTodayExerciseCount.text = getString(R.string.exercises_count_format, todayRoutine.exercises.size)
        } else {
            binding.llTodayContent.visibility = View.GONE
            binding.llNoWorkout.visibility = View.VISIBLE
        }
    }

    private fun updateProgress(completed: Int, total: Int) {
        binding.tvProgressCount.text = getString(R.string.progress_count_format, completed, total)

        val progress = if (total > 0) (completed * 100) / total else 0
        binding.progressIndicator.progress = progress
    }

    private fun navigateToRoutineDetail(routine: RoutineWithExercises) {
        val bundle = Bundle().apply {
            putLong("routineId", routine.routine.id)
        }
        findNavController().navigate(R.id.RoutinesFragment, bundle)
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
