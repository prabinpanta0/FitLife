package com.example.fitlife.data.dao

import androidx.room.*
import com.example.fitlife.data.model.GeoLocation
import com.example.fitlife.data.model.LocationType
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: GeoLocation): Long

    @Update
    suspend fun update(location: GeoLocation)

    @Delete
    suspend fun delete(location: GeoLocation)

    @Query("SELECT * FROM geo_locations WHERE id = :id")
    suspend fun getById(id: Long): GeoLocation?

    @Query("SELECT * FROM geo_locations WHERE userId = :userId ORDER BY name")
    fun getByUserId(userId: Long): Flow<List<GeoLocation>>

    @Query("SELECT * FROM geo_locations WHERE userId = :userId AND locationType = :type ORDER BY name")
    fun getByType(userId: Long, type: LocationType): Flow<List<GeoLocation>>

    @Query("SELECT * FROM geo_locations WHERE userId = :userId")
    suspend fun getAllLocationsSync(userId: Long): List<GeoLocation>

    @Query("DELETE FROM geo_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
