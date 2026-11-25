package com.prakhar.lanman.domain.repository

import com.prakhar.lanman.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfile(): Flow<Profile?>
    suspend fun createProfile(displayName: String)
    suspend fun upsertProfile(profile: Profile)
    suspend fun hasProfile(): Boolean
    suspend fun updateDisplayName(name: String)
}
