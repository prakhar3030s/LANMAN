package com.example.lanman.data.repository

import com.example.lanman.data.local.dao.FileTransferDao
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferRepositoryImpl @Inject constructor(
    private val fileTransferDao: FileTransferDao
) : FileTransferRepository {

    override fun getByMessageId(messageId: String): Flow<FileTransfer?> {
        return fileTransferDao.getByMessageId(messageId).map { it?.toDomain() }
    }

    override fun getByMessageIds(messageIds: List<String>): Flow<List<FileTransfer>> {
        return fileTransferDao.getByMessageIds(messageIds).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getForContactSync(contactId: String): List<FileTransfer> {
        return fileTransferDao.getForContactSync(contactId).map { it.toDomain() }
    }

    override suspend fun insertFileTransfer(fileTransfer: FileTransfer) {
        fileTransferDao.insertFileTransfer(fileTransfer.toEntity())
    }

    override suspend fun updateLocalUri(transferId: String, localUri: String) {
        fileTransferDao.updateLocalUri(transferId, localUri)
    }
}
