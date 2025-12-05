package com.example.fitlife.data.repository

import com.example.fitlife.data.dao.WorkoutRoutineDao
import com.example.fitlife.data.dao.ExerciseDao
import com.example.fitlife.data.dao.EquipmentDao
import com.example.fitlife.data.model.*
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(
    private val routineDao: WorkoutRoutineDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao
) {
    // Routine operations
    suspend fun insertRoutine(routine: WorkoutRoutine): Long = routineDao.insert(routine)

    suspend fun updateRoutine(routine: WorkoutRoutine) = routineDao.update(routine)

    suspend fun deleteRoutine(routine: WorkoutRoutine) = routineDao.delete(routine)

    suspend fun getRoutineById(id: Long): WorkoutRoutine? = routineDao.getById(id)

    fun getRoutinesByUserId(userId: Long): Flow<List<WorkoutRoutine>> = routineDao.getByUserId(userId)

    fun getRoutinesByDayOfWeek(userId: Long, dayOfWeek: Int): Flow<List<WorkoutRoutine>> =
        routineDao.getByDayOfWeek(userId, dayOfWeek)

    fun getScheduledRoutines(userId: Long): Flow<List<WorkoutRoutine>> =
        routineDao.getScheduledRoutines(userId)

    fun getPendingRoutines(userId: Long): Flow<List<WorkoutRoutine>> =
        routineDao.getPendingRoutines(userId)

    suspend fun getRoutineWithExercises(id: Long): RoutineWithExercises? =
        routineDao.getRoutineWithExercises(id)

    fun getRoutinesWithExercises(userId: Long): Flow<List<RoutineWithExercises>> =
        routineDao.getRoutinesWithExercises(userId)

    suspend fun getRoutineWithExercisesAndEquipment(id: Long): RoutineWithExercisesAndEquipment? =
        routineDao.getRoutineWithExercisesAndEquipment(id)

    fun getScheduledRoutinesWithEquipment(userId: Long): Flow<List<RoutineWithExercisesAndEquipment>> =
        routineDao.getScheduledRoutinesWithEquipment(userId)

    suspend fun updateRoutineCompletionStatus(id: Long, isCompleted: Boolean) =
        routineDao.updateCompletionStatus(id, isCompleted)

    suspend fun updateRoutineLocation(id: Long, locationId: Long?) =
        routineDao.updateLocation(id, locationId)

    fun getCompletedRoutineCount(userId: Long): Flow<Int> = routineDao.getCompletedCount(userId)

    fun getTotalScheduledRoutineCount(userId: Long): Flow<Int> = routineDao.getTotalScheduledCount(userId)

    fun getRoutinesByLocation(locationId: Long): Flow<List<WorkoutRoutine>> =
        routineDao.getRoutinesByLocation(locationId)

    fun getRoutinesWithLocations(userId: Long): Flow<List<RoutineWithLocation>> =
        routineDao.getRoutinesWithLocations(userId)

    // Exercise operations
    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise)

    suspend fun insertExercises(exercises: List<Exercise>): List<Long> = exerciseDao.insertAll(exercises)

    suspend fun updateExercise(exercise: Exercise) = exerciseDao.update(exercise)

    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    fun getExercisesByRoutineId(routineId: Long): Flow<List<Exercise>> =
        exerciseDao.getByRoutineId(routineId)

    fun getExercisesWithEquipment(routineId: Long): Flow<List<ExerciseWithEquipment>> =
        exerciseDao.getExercisesWithEquipment(routineId)

    suspend fun updateExerciseCompletionStatus(id: Long, isCompleted: Boolean) =
        exerciseDao.updateCompletionStatus(id, isCompleted)

    suspend fun updateExerciseDetails(id: Long, sets: Int, reps: Int, instructions: String) =
        exerciseDao.updateDetails(id, sets, reps, instructions)

    suspend fun deleteExercisesByRoutineId(routineId: Long) = exerciseDao.deleteByRoutineId(routineId)

    fun getCompletedExerciseCountByRoutine(routineId: Long): Flow<Int> =
        exerciseDao.getCompletedCountByRoutine(routineId)

    fun getTotalExerciseCountByRoutine(routineId: Long): Flow<Int> =
        exerciseDao.getTotalCountByRoutine(routineId)

    // Equipment operations
    suspend fun insertEquipment(equipment: Equipment): Long = equipmentDao.insert(equipment)

    suspend fun insertEquipmentList(equipment: List<Equipment>): List<Long> =
        equipmentDao.insertAll(equipment)

    suspend fun updateEquipment(equipment: Equipment) = equipmentDao.update(equipment)

    suspend fun deleteEquipment(equipment: Equipment) = equipmentDao.delete(equipment)

    suspend fun getEquipmentById(id: Long): Equipment? = equipmentDao.getById(id)

    fun getEquipmentByExerciseId(exerciseId: Long): Flow<List<Equipment>> =
        equipmentDao.getByExerciseId(exerciseId)

    suspend fun updateEquipmentCheckedStatus(id: Long, isChecked: Boolean) =
        equipmentDao.updateCheckedStatus(id, isChecked)

    suspend fun deleteEquipmentByExerciseId(exerciseId: Long) =
        equipmentDao.deleteByExerciseId(exerciseId)

    fun getAllEquipmentForUser(userId: Long): Flow<List<Equipment>> =
        equipmentDao.getAllEquipmentForUser(userId)

    fun getEquipmentByCategory(userId: Long, category: EquipmentCategory): Flow<List<Equipment>> =
        equipmentDao.getEquipmentByCategory(userId, category)
}
