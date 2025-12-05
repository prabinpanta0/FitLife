package com.example.fitlife.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipment",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class Equipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: Long,
    val name: String,
    val category: EquipmentCategory = EquipmentCategory.OTHER,
    val isChecked: Boolean = false
)

enum class EquipmentCategory(val displayName: String) {
    STRENGTH("Strength Equipment"),
    CARDIO("Cardio Equipment"),
    MATS("Mats & Flooring"),
    ACCESSORIES("Accessories"),
    WEIGHTS("Weights"),
    RESISTANCE("Resistance Bands"),
    OTHER("Other")
}
