package com.prakhar.lanman.domain.model

data class FileTransferProgress(
    val fileTransferId: String,
    val bytesTransferred: Long,
    val totalBytes: Long
)
