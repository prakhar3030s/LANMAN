package com.example.lanman.ui.chat

import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message

data class MessageUiModel(
    val message: Message,
    val fileTransfer: FileTransfer? = null,
    val progress: Float? = null
)
