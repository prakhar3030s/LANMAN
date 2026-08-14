package com.example.lanman.domain.repository

import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface FileTransferRepository {
    fun getByMessageId(messageId: String): Flow<FileTransfer?>
    fun getByMessageIds(messageIds: List<String>): Flow<List<FileTransfer>>
    suspend fun insertFileTransfer(fileTransfer: FileTransfer)
}
