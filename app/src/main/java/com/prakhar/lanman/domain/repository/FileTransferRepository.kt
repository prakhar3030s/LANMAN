package com.prakhar.lanman.domain.repository

import com.prakhar.lanman.domain.model.FileTransfer
import kotlinx.coroutines.flow.Flow

interface FileTransferRepository {
    fun getByMessageId(messageId: String): Flow<FileTransfer?>
    fun getByMessageIds(messageIds: List<String>): Flow<List<FileTransfer>>
    suspend fun getForContactSync(contactId: String): List<FileTransfer>
    suspend fun insertFileTransfer(fileTransfer: FileTransfer)
    suspend fun updateLocalUri(transferId: String, localUri: String)
}
