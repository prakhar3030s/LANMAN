package com.prakhar.lanman.ui.chat

import com.prakhar.lanman.domain.model.FileTransfer
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.network.TransferMetrics

data class MessageUiModel(
    val message: Message,
    val fileTransfer: FileTransfer? = null,
    val progress: Float? = null,
    val transferMetrics: TransferMetrics? = null
)
