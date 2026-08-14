package com.example.lanman.domain.model

data class FileTransfer(
    val id: String,
    val messageId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val localUri: String,
    val thumbnailUri: String?
)
