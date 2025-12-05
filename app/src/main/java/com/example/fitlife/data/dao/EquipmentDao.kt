package com.example.fitlife.data.dao

import androidx.room.*
import com.example.fitlife.data.model.Equipment
import com.example.fitlife.data.model.EquipmentCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: Equipment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipment: List<Equipment>): List<Long>

    @Update
    suspend fun update(equipment: Equipment)

    @Delete
    suspend fun delete(equipment: Equipment)

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: Long): Equipment?

    @Query("SELECT * FROM equipment WHERE exerciseId = :exerciseId")
    fun getByExerciseId(exerciseId: Long): Flow<List<Equipment>>

    @Query("UPDATE equipment SET isChecked = :isChecked WHERE id = :id")
    suspend fun updateCheckedStatus(id: Long, isChecked: Boolean)

    @Query("DELETE FROM equipment WHERE exerciseId = :exerciseId")
    suspend fun deleteByExerciseId(exerciseId: Long)

    // Get all equipment for a user's scheduled routines (for checklist)
    @Query("""
        SELECT DISTINCT e.* FROM equipment e
        INNER JOIN exercises ex ON e.exerciseId = ex.id
        INNER JOIN workout_routines wr ON ex.routineId = wr.id
        WHERE wr.userId = :userId AND wr.dayOfWeek >= 0
        ORDER BY e.category, e.name
    """)
    fun getAllEquipmentForUser(userId: Long): Flow<List<Equipment>>

    // Get equipment grouped by category for a user
    @Query("""
        SELECT DISTINCT e.* FROM equipment e
        INNER JOIN exercises ex ON e.exerciseId = ex.id
        INNER JOIN workout_routines wr ON ex.routineId = wr.id
        WHERE wr.userId = :userId AND wr.dayOfWeek >= 0 AND e.category = :category
        ORDER BY e.name
    """)
    fun getEquipmentByCategory(userId: Long, category: EquipmentCategory): Flow<List<Equipment>>
}
