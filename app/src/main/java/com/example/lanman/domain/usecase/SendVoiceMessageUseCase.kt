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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        val fileTransferId = UUID.randomUUID().toString()

        // The recorder hands us a content:// URI backed by the cache directory, so resolve it
        // through the content resolver and keep our own copy in app-private storage. The cache
        // can be trimmed at any time, and a plain File(uri.path) does not exist for content URIs.
        val voiceDir = File(context.filesDir, "voice_recordings")
        if (!voiceDir.exists() && !voiceDir.mkdirs()) {
            throw IOException("Could not create the voice recordings directory")
        }
        val audioFile = File(voiceDir, "$fileTransferId.m4a")
        try {
            val source = context.contentResolver.openInputStream(fileUri)
                ?: throw IOException("Could not open the recorded audio: $fileUri")
            source.use { input ->
                FileOutputStream(audioFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            audioFile.delete()
            throw e
        }
        if (audioFile.length() <= 0L) {
            audioFile.delete()
            throw IOException("The recorded audio is empty")
        }
        // Drop the recorder's temporary copy now that the durable one exists.
        val sourcePath = fileUri.path
        if (sourcePath != null && sourcePath.startsWith(context.cacheDir.absolutePath)) {
            File(sourcePath).delete()
        } else {
            // FileProvider content:// URIs only expose the file name, not the real path.
            val cachedName = fileUri.lastPathSegment
            if (!cachedName.isNullOrBlank() && cachedName.endsWith(".m4a") && File(cachedName).name == cachedName) {
                File(File(context.cacheDir, "voice_recordings"), cachedName).delete()
            }
        }

        val fileSize = audioFile.length()
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

        val keyFile = File(keysDir, fileTransferId)
        FileOutputStream(keyFile).use { out ->
            out.write(aesKey)
            out.write(baseNonce)
        }

        // Create Message and FileTransfer in DB pointing to the durable copy
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
            // Display name only: the receiver stores the payload under a unique transfer id.
            fileName = "Voice Message",
            fileSizeBytes = fileSize,
            mimeType = mimeType,
            localUri = audioFile.absolutePath,
            thumbnailUri = audioFile.absolutePath
        )

        messageRepository.insertMessage(message)
        fileTransferRepository.insertFileTransfer(fileTransfer)
    }
}
