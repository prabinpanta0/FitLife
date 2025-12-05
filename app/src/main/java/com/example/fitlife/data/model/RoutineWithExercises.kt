package com.example.fitlife.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class RoutineWithExercises(
    @Embedded val routine: WorkoutRoutine,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val exercises: List<Exercise>
)

data class ExerciseWithEquipment(
    @Embedded val exercise: Exercise,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseId"
    )
    val equipment: List<Equipment>
)

data class RoutineWithExercisesAndEquipment(
    @Embedded val routine: WorkoutRoutine,
    @Relation(
        entity = Exercise::class,
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val exercisesWithEquipment: List<ExerciseWithEquipment>
)

data class RoutineWithLocation(
    @Embedded val routine: WorkoutRoutine,
    @Relation(
        parentColumn = "locationId",
        entityColumn = "id"
    )
    val location: GeoLocation?
)
