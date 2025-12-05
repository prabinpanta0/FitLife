package com.example.fitlife.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geo_locations",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class GeoLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: LocationType = LocationType.GYM,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class LocationType(val displayName: String, val emoji: String) {
    GYM("Gym", "🏋️"),
    YOGA_STUDIO("Yoga Studio", "🧘"),
    PARK("Park", "🌳"),
    HOME("Home", "🏠"),
    POOL("Swimming Pool", "🏊"),
    OTHER("Other", "📍")
}
