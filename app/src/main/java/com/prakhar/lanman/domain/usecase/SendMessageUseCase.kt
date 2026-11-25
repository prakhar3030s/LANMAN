package com.prakhar.lanman.domain.usecase

import android.util.Log
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.protocol.Frame
import com.prakhar.lanman.domain.repository.MessageRepository
import com.prakhar.lanman.domain.repository.ProfileRepository
import com.prakhar.lanman.domain.security.EncryptionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val connectionManager: ConnectionManager,
    private val encryptionEngine: EncryptionEngine
) {
    private val TAG = "SendMessageUseCase"
    private val lastSendTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend operator fun invoke(contactId: String, text: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastSend = lastSendTimes[contactId] ?: 0L
        if (now - lastSend < 300) {
            return@withContext // Debounce rapid double-taps
        }
        lastSendTimes[contactId] = now
        
        val messageId = UUID.randomUUID().toString()
        val contentBytes = text.toByteArray(StandardCharsets.UTF_8)

        // Persist before sending: the receiver ACKs immediately, and an ACK that arrives
        // before the row exists is dropped, leaving the message stuck as unconfirmed.
        val message = Message(
            id = messageId,
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            type = MessageType.TEXT,
            content = contentBytes,
            status = MessageStatus.PENDING,
            timestamp = now
        )
        messageRepository.insertMessage(message)

        // Fast-Path: If connection is already open, send directly over socket immediately
        if (connectionManager.hasActiveConnection(contactId)) {
            try {
                val myId = profileRepository.getProfile().firstOrNull()?.id
                if (myId != null) {
                    val ciphertext = encryptionEngine.encrypt(contactId, contentBytes)
                    val frame = Frame.MessageFrame(
                        messageId = messageId,
                        senderId = myId,
                        messageType = 0, // TEXT
                        timestamp = now,
                        content = ciphertext
                    )
                    val result = connectionManager.sendFrame(contactId, frame)
                    if (result.isSuccess) {
                        // Stay DELIVERED if the ACK already landed while we were sending.
                        messageRepository.updateMessageStatusIfCurrent(
                            messageId,
                            MessageStatus.PENDING,
                            MessageStatus.SENT
                        )
                        Log.d(TAG, "Fast-path delivered message $messageId directly over active socket")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path send failed, will be retried by the dispatcher", e)
            }
        }
    }
}
