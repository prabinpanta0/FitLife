package com.example.fitlife.ui.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.R
import com.example.fitlife.data.model.Exercise
import com.example.fitlife.databinding.ItemExerciseBinding

class ExerciseAdapter(
    private val onExerciseClick: (Exercise) -> Unit,
    private val onDeleteClick: (Exercise) -> Unit,
    private val onCompletionToggle: (Exercise, Boolean) -> Unit
) : ListAdapter<Exercise, ExerciseAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val binding = ItemExerciseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExerciseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExerciseViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(exercise: Exercise) {
            binding.apply {
                tvExerciseName.text = exercise.name
                tvSetsReps.text = root.context.getString(R.string.sets_reps_format, exercise.sets, exercise.reps)
                
                // Display image or emoji
                when {
                    exercise.imageUri != null -> {
                        try {
                            ivExerciseImage.setImageURI(Uri.parse(exercise.imageUri))
                            ivExerciseImage.visibility = View.VISIBLE
                            tvExerciseEmoji.visibility = View.GONE
                        } catch (e: Exception) {
                            // Fallback to emoji
                            ivExerciseImage.visibility = View.GONE
                            tvExerciseEmoji.visibility = View.VISIBLE
                            tvExerciseEmoji.text = exercise.imageEmoji
                        }
                    }
                    exercise.imageResourceName != null -> {
                        val resId = root.context.resources.getIdentifier(
                            exercise.imageResourceName, 
                            "drawable", 
                            root.context.packageName
                        )
                        if (resId != 0) {
                            ivExerciseImage.setImageResource(resId)
                            ivExerciseImage.visibility = View.VISIBLE
                            tvExerciseEmoji.visibility = View.GONE
                        } else {
                            ivExerciseImage.visibility = View.GONE
                            tvExerciseEmoji.visibility = View.VISIBLE
                            tvExerciseEmoji.text = exercise.imageEmoji
                        }
                    }
                    else -> {
                        ivExerciseImage.visibility = View.GONE
                        tvExerciseEmoji.visibility = View.VISIBLE
                        tvExerciseEmoji.text = exercise.imageEmoji
                    }
                }

                // Instructions
                if (exercise.instructions.isNotEmpty()) {
                    tvInstructions.text = exercise.instructions
                    tvInstructions.visibility = View.VISIBLE
                } else {
                    tvInstructions.visibility = View.GONE
                }

                // Completion status
                cbCompleted.isChecked = exercise.isCompleted
                tvExerciseName.alpha = if (exercise.isCompleted) 0.6f else 1.0f

                // Click listeners
                root.setOnClickListener {
                    onExerciseClick(exercise)
                }

                ivDelete.setOnClickListener {
                    onDeleteClick(exercise)
                }

                cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                    onCompletionToggle(exercise, isChecked)
                }
            }
        }
    }

    class ExerciseDiffCallback : DiffUtil.ItemCallback<Exercise>() {
        override fun areItemsTheSame(oldItem: Exercise, newItem: Exercise): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Exercise, newItem: Exercise): Boolean {
            return oldItem == newItem
        }
    }
}
