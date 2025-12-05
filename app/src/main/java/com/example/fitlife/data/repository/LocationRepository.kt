package com.example.fitlife.data.repository

import com.example.fitlife.data.dao.GeoLocationDao
import com.example.fitlife.data.model.GeoLocation
import com.example.fitlife.data.model.LocationType
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: GeoLocationDao) {

    suspend fun insertLocation(location: GeoLocation): Long = locationDao.insert(location)

    suspend fun updateLocation(location: GeoLocation) = locationDao.update(location)

    suspend fun deleteLocation(location: GeoLocation) = locationDao.delete(location)

    suspend fun getLocationById(id: Long): GeoLocation? = locationDao.getById(id)

    fun getLocationsByUserId(userId: Long): Flow<List<GeoLocation>> = locationDao.getByUserId(userId)

    fun getLocationsByType(userId: Long, type: LocationType): Flow<List<GeoLocation>> =
        locationDao.getByType(userId, type)

    suspend fun getAllLocationsSync(userId: Long): List<GeoLocation> =
        locationDao.getAllLocationsSync(userId)

    suspend fun deleteLocationById(id: Long) = locationDao.deleteById(id)
}
