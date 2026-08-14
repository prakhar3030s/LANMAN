package com.example.lanman.domain.network

import com.example.lanman.domain.model.FileTransferProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferProgressTracker @Inject constructor() {
    private val _progress = MutableSharedFlow<FileTransferProgress>(extraBufferCapacity = 64)
    val progress: Flow<FileTransferProgress> = _progress.asSharedFlow()

    fun updateProgress(fileTransferId: String, bytesTransferred: Long, totalBytes: Long) {
        _progress.tryEmit(FileTransferProgress(fileTransferId, bytesTransferred, totalBytes))
    }
}
