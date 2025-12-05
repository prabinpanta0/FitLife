package com.example.fitlife.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val instructions: String = "",
    val imageEmoji: String = "💪", // Using emoji as placeholder for images
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0
)
