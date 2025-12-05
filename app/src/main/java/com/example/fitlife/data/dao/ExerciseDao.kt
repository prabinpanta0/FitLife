package com.example.fitlife.data.dao

import androidx.room.*
import com.example.fitlife.data.model.Exercise
import com.example.fitlife.data.model.ExerciseWithEquipment
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE routineId = :routineId ORDER BY orderIndex")
    fun getByRoutineId(routineId: Long): Flow<List<Exercise>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE routineId = :routineId ORDER BY orderIndex")
    fun getExercisesWithEquipment(routineId: Long): Flow<List<ExerciseWithEquipment>>

    @Query("UPDATE exercises SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateCompletionStatus(id: Long, isCompleted: Boolean)

    @Query("UPDATE exercises SET sets = :sets, reps = :reps, instructions = :instructions WHERE id = :id")
    suspend fun updateDetails(id: Long, sets: Int, reps: Int, instructions: String)

    @Query("DELETE FROM exercises WHERE routineId = :routineId")
    suspend fun deleteByRoutineId(routineId: Long)

    @Query("SELECT COUNT(*) FROM exercises WHERE routineId = :routineId AND isCompleted = 1")
    fun getCompletedCountByRoutine(routineId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercises WHERE routineId = :routineId")
    fun getTotalCountByRoutine(routineId: Long): Flow<Int>
}
