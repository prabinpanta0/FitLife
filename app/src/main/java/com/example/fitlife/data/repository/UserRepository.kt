package com.example.fitlife.data.repository

import com.example.fitlife.data.dao.UserDao
import com.example.fitlife.data.model.User
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {

    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            if (userDao.emailExists(email)) {
                Result.failure(Exception("Email already registered"))
            } else {
                val user = User(email = email, password = password, name = name)
                val id = userDao.insert(user)
                Result.success(user.copy(id = id))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val user = userDao.login(email, password)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid email or password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserById(id: Long): User? = userDao.getById(id)

    suspend fun getUserByEmail(email: String): User? = userDao.getByEmail(email)

    suspend fun updateUser(user: User) = userDao.update(user)

    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()
}
