package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    fun getProfile(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM profiles LIMIT 1)")
    suspend fun hasProfile(): Boolean

    @Query("UPDATE profiles SET displayName = :name")
    suspend fun updateDisplayName(name: String)
}
