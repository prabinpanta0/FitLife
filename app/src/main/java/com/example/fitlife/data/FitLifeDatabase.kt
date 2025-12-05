package com.example.fitlife.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.fitlife.data.dao.*
import com.example.fitlife.data.model.*

@Database(
    entities = [
        User::class,
        WorkoutRoutine::class,
        Exercise::class,
        Equipment::class,
        GeoLocation::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FitLifeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutRoutineDao(): WorkoutRoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun geoLocationDao(): GeoLocationDao

    companion object {
        @Volatile
        private var INSTANCE: FitLifeDatabase? = null

        fun getDatabase(context: Context): FitLifeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitLifeDatabase::class.java,
                    "fitlife_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
