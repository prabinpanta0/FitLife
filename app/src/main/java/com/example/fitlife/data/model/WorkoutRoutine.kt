package com.example.fitlife.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_routines",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GeoLocation::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("userId"), Index("locationId")]
)
data class WorkoutRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val description: String = "",
    val dayOfWeek: Int = -1, // 0-6 for Sun-Sat, -1 for unscheduled (kept for backward compatibility)
    val daysOfWeek: String = "", // Comma-separated days (e.g., "0,1,3" for Sun, Mon, Wed)
    val isCompleted: Boolean = false,
    val locationId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Helper functions for multi-day support
    fun getDaysAsList(): List<Int> {
        return if (daysOfWeek.isNotBlank()) {
            daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        } else if (dayOfWeek >= 0) {
            listOf(dayOfWeek)
        } else {
            emptyList()
        }
    }
    
    fun containsDay(day: Int): Boolean {
        return getDaysAsList().contains(day)
    }
    
    companion object {
        fun daysListToString(days: List<Int>): String {
            return days.sorted().joinToString(",")
        }
    }
}
