package com.example.lanman.domain.usecase

import android.util.Log
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.ConnectionManager
import com.example.lanman.domain.network.protocol.Frame
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.security.EncryptionEngine
import kotlinx.coroutines.flow.firstOrNull
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

    suspend operator fun invoke(contactId: String, text: String) {
        val now = System.currentTimeMillis()
        val lastSend = lastSendTimes[contactId] ?: 0L
        if (now - lastSend < 300) {
            return // Debounce rapid double-taps
        }
        lastSendTimes[contactId] = now
        
        val messageId = UUID.randomUUID().toString()
        val contentBytes = text.toByteArray(StandardCharsets.UTF_8)
        
        // Fast-Path: If connection is already open, send directly over socket immediately
        var dispatchedImmediately = false
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
                        dispatchedImmediately = true
                        Log.d(TAG, "Fast-path delivered message $messageId directly over active socket")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path send failed, will persist as PENDING for dispatcher", e)
            }
        }

        val message = Message(
            id = messageId,
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            type = MessageType.TEXT,
            content = contentBytes,
            status = if (dispatchedImmediately) MessageStatus.SENT else MessageStatus.PENDING,
            timestamp = now
        )
        
        messageRepository.insertMessage(message)
    }
}
