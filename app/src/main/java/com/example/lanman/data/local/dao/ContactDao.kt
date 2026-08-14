package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("""
        SELECT c.*,
               m.content AS lastMessageContent,
               m.timestamp AS lastMessageTimestamp,
               (SELECT COUNT(*) FROM messages WHERE contactId = c.id AND direction = 'INCOMING' AND status != 'READ') AS unreadCount
        FROM contacts c
        LEFT JOIN messages m ON m.id = (
            SELECT id FROM messages WHERE contactId = c.id ORDER BY timestamp DESC LIMIT 1
        )
        ORDER BY COALESCE(m.timestamp, c.addedAt) DESC
    """)
    fun getChatListContacts(): Flow<List<com.example.lanman.data.local.entity.ChatListInfoEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun getContactById(id: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)

    @Query("UPDATE contacts SET lastKnownIp = :ip, lastSeenAt = :timestamp WHERE id = :id")
    suspend fun updateLastKnownIp(id: String, ip: String?, timestamp: Long?)
}
