package com.example.fitlife.ui.adapters

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import com.example.fitlife.R
import com.example.fitlife.data.model.Exercise
import com.example.fitlife.databinding.ItemExerciseBinding
import java.io.File

class ExerciseAdapter(
    private val onExerciseClick: (Exercise) -> Unit,
    private val onDeleteClick: (Exercise) -> Unit,
    private val onCompletionToggle: (Exercise, Boolean) -> Unit
) : ListAdapter<Exercise, ExerciseAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    companion object {
        private const val TAG = "ExerciseAdapter"
        
        // Prefix to identify relative paths stored in the database
        private const val RELATIVE_PATH_PREFIX = "@filesDir/"
        
        // Maximum number of cached resource IDs to prevent unbounded memory growth
        private const val MAX_CACHE_SIZE = 50
        
        // LRU cache for drawable resource IDs to avoid repeated getIdentifier() calls
        private val resourceIdCache = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    }
    
    /**
     * Resolves and caches a drawable resource ID by name.
     * Returns 0 if the resource is not found.
     */
    private fun getDrawableResourceId(context: android.content.Context, resourceName: String): Int {
        return resourceIdCache.getOrPut(resourceName) {
            context.resources.getIdentifier(resourceName, "drawable", context.packageName).also { resId ->
                if (resId == 0) {
                    Log.w(TAG, "Drawable resource not found: $resourceName")
                }
            }
        }
    }

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
    
    override fun onViewRecycled(holder: ExerciseViewHolder) {
        super.onViewRecycled(holder)
        holder.clearImage()
    }

    inner class ExerciseViewHolder(
        private val binding: ItemExerciseBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        /**
         * Clears the image view to prevent stale images and cancels any pending Glide requests.
         */
        fun clearImage() {
            Glide.with(binding.root.context).clear(binding.ivExerciseImage)
            binding.ivExerciseImage.setImageDrawable(null)
        }

        fun bind(exercise: Exercise) {
            binding.apply {
                tvExerciseName.text = exercise.name
                tvSetsReps.text = root.context.getString(R.string.sets_reps_format, exercise.sets, exercise.reps)
                
                // Clear previous image state at the start of bind
                clearImage()
                
                // Display image or emoji
                when {
                    // User-captured/selected image (local URI or cloud URL)
                    !exercise.imageUri.isNullOrEmpty() -> {
                        loadImageFromUri(exercise.imageUri, exercise.imageEmoji)
                    }
                    // Preset drawable image
                    !exercise.imageResourceName.isNullOrEmpty() -> {
                        loadImageFromResource(exercise.imageResourceName, exercise.imageEmoji)
                    }
                    // Fallback to emoji
                    else -> {
                        showEmoji(exercise.imageEmoji)
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
        
        /**
         * Loads an image from a URI (local file or cloud URL) using Glide.
         * Supports relative paths (prefixed with @filesDir/), absolute paths, and URI strings.
         */
        private fun loadImageFromUri(imageUri: String, fallbackEmoji: String) {
            val context = binding.root.context
            
            // Validate input - show emoji for blank/empty URIs
            if (imageUri.isBlank()) {
                Log.w(TAG, "Empty or blank image URI, falling back to emoji")
                showEmoji(fallbackEmoji)
                return
            }
            
            // Resolve the image source based on format
            val loadSource: Any = when {
                // Relative path stored with prefix - reconstruct full path
                imageUri.startsWith(RELATIVE_PATH_PREFIX) -> {
                    val relativePath = imageUri.removePrefix(RELATIVE_PATH_PREFIX)
                    File(context.filesDir, relativePath)
                }
                // Cloud URL - pass directly to Glide
                imageUri.startsWith("http://") || imageUri.startsWith("https://") -> imageUri
                // File URI - Uri.parse is lenient and won't throw
                imageUri.startsWith("file://") -> Uri.parse(imageUri)
                // Content URI - Uri.parse is lenient and won't throw
                imageUri.startsWith("content://") -> Uri.parse(imageUri)
                // Legacy absolute path (e.g., /data/...) - load as File
                imageUri.startsWith("/") -> File(imageUri)
                // Fallback: parse as URI (Uri.parse is lenient)
                else -> Uri.parse(imageUri)
            }
            
            Glide.with(context)
                .load(loadSource)
                .centerCrop()
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Failed to load image from URI: $imageUri", e)
                        // Show emoji as fallback on error
                        binding.root.post { showEmoji(fallbackEmoji) }
                        return true // We handled the error
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false // Let Glide handle setting the resource
                    }
                })
                .into(binding.ivExerciseImage)
            
            binding.cardExerciseImage.visibility = View.VISIBLE
            binding.tvExerciseEmoji.visibility = View.GONE
        }
        
        /**
         * Loads an image from a drawable resource using Glide with cached resource ID lookup.
         */
        private fun loadImageFromResource(resourceName: String, fallbackEmoji: String) {
            val context = binding.root.context
            val resId = getDrawableResourceId(context, resourceName)
            
            if (resId == 0) {
                Log.w(TAG, "Resource not found, falling back to emoji: $resourceName")
                showEmoji(fallbackEmoji)
                return
            }
            
            Glide.with(context)
                .load(resId)
                .centerCrop()
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Failed to load drawable resource: $resourceName (id=$resId)", e)
                        binding.root.post { showEmoji(fallbackEmoji) }
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
                .into(binding.ivExerciseImage)
            
            binding.cardExerciseImage.visibility = View.VISIBLE
            binding.tvExerciseEmoji.visibility = View.GONE
        }
        
        /**
         * Shows the emoji fallback and hides the image view.
         */
        private fun showEmoji(emoji: String) {
            binding.tvExerciseEmoji.text = emoji
            binding.tvExerciseEmoji.visibility = View.VISIBLE
            binding.cardExerciseImage.visibility = View.GONE
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
