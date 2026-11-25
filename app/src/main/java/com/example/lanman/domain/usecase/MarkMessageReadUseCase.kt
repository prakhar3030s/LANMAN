package com.example.lanman.domain.usecase

import android.util.Log
import com.example.lanman.data.notification.NotificationPreferences
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MarkMessageReadUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val connectionManager: ConnectionManager,
    private val notificationPreferences: NotificationPreferences
) {
    private val TAG = "MarkMessageReadUseCase"

    suspend operator fun invoke(messageId: String) = withContext(Dispatchers.IO) {
        val message = messageRepository.getMessageById(messageId) ?: return@withContext

        if (message.direction == MessageDirection.INCOMING && message.status == MessageStatus.DELIVERED) {
            messageRepository.updateMessageStatus(messageId, MessageStatus.READ)
            Log.d(TAG, "Message $messageId marked as READ locally")

            if (!notificationPreferences.readReceiptsEnabled) {
                return@withContext
            }

            val frame = Frame.ReadFrame(messageId)
            val result = connectionManager.sendFrame(message.contactId, frame)
            if (result.isFailure) {
                Log.d(TAG, "Peer offline while sending READ receipt for $messageId")
            }
        }
    }
}
