package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.MessageEntity
import com.example.lanman.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContact(contactId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND status = :status ORDER BY timestamp ASC")
    suspend fun getPendingMessagesForContact(
        contactId: String,
        status: MessageStatus
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND status IN (:statuses) ORDER BY timestamp ASC")
    suspend fun getUnacknowledgedMessagesForContact(
        contactId: String,
        statuses: List<MessageStatus>
    ): List<MessageEntity>

    @Query("SELECT DISTINCT contactId FROM messages WHERE status IN (:statuses)")
    fun getContactsWithUnacknowledgedMessages(
        statuses: List<MessageStatus>
    ): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @androidx.room.Transaction
    suspend fun insertIfNotExists(message: MessageEntity): Boolean {
        val existing = getMessageById(message.id)
        if (existing == null) {
            insertMessage(message)
            return true
        }
        return false
    }

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: MessageStatus)

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForContact(contactId: String): Flow<MessageEntity?>
}
