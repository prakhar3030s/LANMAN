package com.example.lanman.ui.chat

import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.network.TransferMetrics

data class MessageUiModel(
    val message: Message,
    val fileTransfer: FileTransfer? = null,
    val progress: Float? = null,
    val transferMetrics: TransferMetrics? = null
)
