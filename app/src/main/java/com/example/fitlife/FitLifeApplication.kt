package com.example.fitlife

import android.app.Application
import com.example.fitlife.data.FitLifeDatabase
import com.example.fitlife.data.repository.LocationRepository
import com.example.fitlife.data.repository.UserRepository
import com.example.fitlife.data.repository.WorkoutRepository

class FitLifeApplication : Application() {

    val database by lazy { FitLifeDatabase.getDatabase(this) }

    val userRepository by lazy { UserRepository(database.userDao()) }

    val workoutRepository by lazy {
        WorkoutRepository(
            database.workoutRoutineDao(),
            database.exerciseDao(),
            database.equipmentDao()
        )
    }

    val locationRepository by lazy { LocationRepository(database.geoLocationDao()) }
}
