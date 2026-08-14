package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.MessageRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageRequestDao {
    @Query("SELECT * FROM message_requests ORDER BY timestamp DESC")
    fun getAllMessageRequests(): Flow<List<MessageRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageRequest(request: MessageRequestEntity)

    @Query("DELETE FROM message_requests WHERE contactId = :contactId")
    suspend fun deleteMessageRequest(contactId: String)
}
