package com.example.fitlife.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val password: String, // In production, this should be hashed
    val name: String,
    val profilePhotoUri: String? = null, // URI for user's profile photo
    val createdAt: Long = System.currentTimeMillis()
)
