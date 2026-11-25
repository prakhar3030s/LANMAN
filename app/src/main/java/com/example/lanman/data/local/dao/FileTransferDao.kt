package com.example.lanman.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lanman.data.local.entity.FileTransferEntity
import com.example.lanman.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {
    @Query("SELECT * FROM file_transfers WHERE messageId = :messageId LIMIT 1")
    fun getByMessageId(messageId: String): Flow<FileTransferEntity?>

    @Query("SELECT * FROM file_transfers WHERE messageId IN (:messageIds)")
    fun getByMessageIds(messageIds: List<String>): Flow<List<FileTransferEntity>>

    @Query("""
        SELECT ft.* FROM file_transfers ft
        INNER JOIN messages m ON ft.messageId = m.id
        WHERE m.contactId = :contactId
    """)
    suspend fun getForContactSync(contactId: String): List<FileTransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileTransfer(entity: FileTransferEntity)

    @Query("UPDATE file_transfers SET localUri = :localUri WHERE id = :transferId")
    suspend fun updateLocalUri(transferId: String, localUri: String)
}
