package com.example.lanman.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.security.StreamingEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

class SendVoiceMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository
) {
    suspend operator fun invoke(contactId: String, fileUri: Uri, durationMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        val file = File(fileUri.path ?: return@withContext)
        if (!file.exists()) return@withContext
        val fileSize = file.length()
        val mimeType = "audio/lanman-voice;duration=$durationMs"
        
        // Generate random AES-256 key and 96-bit nonce
        val secureRandom = SecureRandom()
        val aesKey = ByteArray(32)
        val baseNonce = ByteArray(12)
        secureRandom.nextBytes(aesKey)
        secureRandom.nextBytes(baseNonce)
        
        // Save tiny 44-byte key material
        val keysDir = File(context.filesDir, "transfer_keys")
        if (!keysDir.exists()) keysDir.mkdirs()
        
        val fileTransferId = UUID.randomUUID().toString()
        val keyFile = File(keysDir, fileTransferId)
        FileOutputStream(keyFile).use { out ->
            out.write(aesKey)
            out.write(baseNonce)
        }

        // Create Message and FileTransfer in DB pointing directly to original recording
        val messageId = UUID.randomUUID().toString()
        val message = Message(
            id = messageId,
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            type = MessageType.VOICE,
            content = "voice:$durationMs".toByteArray(Charsets.UTF_8),
            status = MessageStatus.PENDING,
            timestamp = now
        )
        
        val fileTransfer = FileTransfer(
            id = fileTransferId,
            messageId = messageId,
            fileName = "Voice Message",
            fileSizeBytes = fileSize,
            mimeType = mimeType,
            localUri = file.absolutePath,
            thumbnailUri = file.absolutePath
        )
        
        messageRepository.insertMessage(message)
        fileTransferRepository.insertFileTransfer(fileTransfer)
    }
}
