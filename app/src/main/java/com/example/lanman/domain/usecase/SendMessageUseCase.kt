package com.example.lanman.domain.usecase

import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.repository.MessageRepository
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    private val lastSendTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend operator fun invoke(contactId: String, text: String) {
        val now = System.currentTimeMillis()
        val lastSend = lastSendTimes[contactId] ?: 0L
        if (now - lastSend < 500) {
            return // Debounce rapid double-taps
        }
        lastSendTimes[contactId] = now
        
        val message = Message(
            id = UUID.randomUUID().toString(),
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            type = MessageType.TEXT,
            content = text.toByteArray(StandardCharsets.UTF_8),
            status = MessageStatus.PENDING,
            timestamp = now
        )
        
        messageRepository.insertMessage(message)
    }
}
