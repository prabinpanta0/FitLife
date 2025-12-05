package com.example.fitlife.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.R
import com.example.fitlife.data.model.RoutineWithExercises
import com.example.fitlife.databinding.ItemRoutineBinding
import com.example.fitlife.utils.DateUtils

class RoutineAdapter(
    private val onRoutineClick: (RoutineWithExercises) -> Unit,
    private val onEditClick: (RoutineWithExercises) -> Unit,
    private val onDeleteClick: (RoutineWithExercises) -> Unit,
    private val onToggleComplete: (RoutineWithExercises) -> Unit
) : ListAdapter<RoutineWithExercises, RoutineAdapter.RoutineViewHolder>(RoutineDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        val binding = ItemRoutineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RoutineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RoutineViewHolder(
        private val binding: ItemRoutineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(routineWithExercises: RoutineWithExercises) {
            val routine = routineWithExercises.routine
            val exercises = routineWithExercises.exercises

            binding.apply {
                tvRoutineName.text = routine.name
                tvExerciseCount.text = root.context.getString(R.string.exercises_count_format, exercises.size)

                // Day indicator
                if (routine.dayOfWeek >= 0) {
                    tvDayIndicator.text = DateUtils.getShortDayName(routine.dayOfWeek)
                    tvDayIndicator.visibility = View.VISIBLE
                } else {
                    tvDayIndicator.text = root.context.getString(R.string.day_indicator_none)
                    tvDayIndicator.visibility = View.VISIBLE
                }

                // Completion status
                if (routine.isCompleted) {
                    ivCompletionStatus.visibility = View.VISIBLE
                    tvRoutineName.alpha = 0.6f
                } else {
                    ivCompletionStatus.visibility = View.GONE
                    tvRoutineName.alpha = 1.0f
                }

                // Click listeners
                root.setOnClickListener {
                    onRoutineClick(routineWithExercises)
                }

                ivMore.setOnClickListener { view ->
                    showPopupMenu(view, routineWithExercises)
                }
            }
        }

        private fun showPopupMenu(view: View, routineWithExercises: RoutineWithExercises) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_routine_item, popup.menu)

            // Update menu item text based on completion status
            popup.menu.findItem(R.id.action_toggle_complete)?.title =
                if (routineWithExercises.routine.isCompleted) "Mark Incomplete" else "Mark Complete"

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick(routineWithExercises)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(routineWithExercises)
                        true
                    }
                    R.id.action_toggle_complete -> {
                        onToggleComplete(routineWithExercises)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class RoutineDiffCallback : DiffUtil.ItemCallback<RoutineWithExercises>() {
        override fun areItemsTheSame(
            oldItem: RoutineWithExercises,
            newItem: RoutineWithExercises
        ): Boolean {
            return oldItem.routine.id == newItem.routine.id
        }

        override fun areContentsTheSame(
            oldItem: RoutineWithExercises,
            newItem: RoutineWithExercises
        ): Boolean {
            return oldItem == newItem
        }
    }
}
