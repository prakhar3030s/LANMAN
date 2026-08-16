package com.example.lanman.domain.usecase

import android.util.Log
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.MessageRepository
import javax.inject.Inject

class MarkMessageReadUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val connectionManager: ConnectionManager
) {
    private val TAG = "MarkMessageReadUseCase"

    suspend operator fun invoke(messageId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val message = messageRepository.getMessageById(messageId) ?: return@withContext

        // Only mark incoming messages as read once they have been delivered, and only if they aren't already marked
        if (message.direction == MessageDirection.INCOMING && message.status == MessageStatus.DELIVERED) {
            // Optimistically update local status
            messageRepository.updateMessageStatus(messageId, MessageStatus.READ)
            Log.d(TAG, "Message $messageId marked as READ locally")

            // Fire off ReadFrame to sender
            val frame = Frame.ReadFrame(messageId)
            val result = connectionManager.sendFrame(message.contactId, frame)
            
            if (result.isSuccess) {
                Log.d(TAG, "Sent READ receipt for $messageId to ${message.contactId}")
            } else {
                Log.w(TAG, "Failed to send READ receipt for $messageId to ${message.contactId}, it will not be retried automatically in Phase 10")
            }
        }
    }
}
