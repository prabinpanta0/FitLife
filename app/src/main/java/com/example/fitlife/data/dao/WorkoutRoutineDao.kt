package com.example.fitlife.data.dao

import androidx.room.*
import com.example.fitlife.data.model.WorkoutRoutine
import com.example.fitlife.data.model.RoutineWithExercises
import com.example.fitlife.data.model.RoutineWithExercisesAndEquipment
import com.example.fitlife.data.model.RoutineWithLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutRoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: WorkoutRoutine): Long

    @Update
    suspend fun update(routine: WorkoutRoutine)

    @Delete
    suspend fun delete(routine: WorkoutRoutine)

    @Query("SELECT * FROM workout_routines WHERE id = :id")
    suspend fun getById(id: Long): WorkoutRoutine?

    @Query("SELECT * FROM workout_routines WHERE userId = :userId ORDER BY dayOfWeek, createdAt DESC")
    fun getByUserId(userId: Long): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM workout_routines WHERE userId = :userId AND dayOfWeek = :dayOfWeek")
    fun getByDayOfWeek(userId: Long, dayOfWeek: Int): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM workout_routines WHERE userId = :userId AND dayOfWeek >= 0")
    fun getScheduledRoutines(userId: Long): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM workout_routines WHERE userId = :userId AND isCompleted = 0 AND dayOfWeek >= 0")
    fun getPendingRoutines(userId: Long): Flow<List<WorkoutRoutine>>

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE id = :id")
    suspend fun getRoutineWithExercises(id: Long): RoutineWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE userId = :userId ORDER BY dayOfWeek, createdAt DESC")
    fun getRoutinesWithExercises(userId: Long): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE id = :id")
    suspend fun getRoutineWithExercisesAndEquipment(id: Long): RoutineWithExercisesAndEquipment?

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE userId = :userId AND dayOfWeek >= 0")
    fun getScheduledRoutinesWithEquipment(userId: Long): Flow<List<RoutineWithExercisesAndEquipment>>

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE locationId = :locationId")
    fun getRoutinesByLocation(locationId: Long): Flow<List<WorkoutRoutine>>

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE userId = :userId AND locationId IS NOT NULL")
    fun getRoutinesWithLocations(userId: Long): Flow<List<RoutineWithLocation>>

    @Query("UPDATE workout_routines SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCompletionStatus(id: Long, isCompleted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workout_routines SET locationId = :locationId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLocation(id: Long, locationId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM workout_routines WHERE userId = :userId AND isCompleted = 1 AND dayOfWeek >= 0")
    fun getCompletedCount(userId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_routines WHERE userId = :userId AND dayOfWeek >= 0")
    fun getTotalScheduledCount(userId: Long): Flow<Int>
}
