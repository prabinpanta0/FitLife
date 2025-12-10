package com.example.fitlife.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * Migration from version 1 to 2:
         * - Adds imageResourceName (TEXT, nullable) to exercises table for preset drawable names
         * - Adds imageUri (TEXT, nullable) to exercises table for user-captured image URIs
         * - Adds profilePhotoUri (TEXT, nullable) to users table for profile photos
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add image columns to exercises table
                database.execSQL("ALTER TABLE exercises ADD COLUMN imageResourceName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE exercises ADD COLUMN imageUri TEXT DEFAULT NULL")
                
                // Add profile photo column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN profilePhotoUri TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): FitLifeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitLifeDatabase::class.java,
                    "fitlife_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Fallback to destructive migration only for future unhandled version changes
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
