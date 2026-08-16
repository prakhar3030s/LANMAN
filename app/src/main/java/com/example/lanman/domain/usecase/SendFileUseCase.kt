package com.example.lanman.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.domain.network.FileTransferProgressTracker
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.security.StreamingEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

class SendFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val progressTracker: FileTransferProgressTracker
) {
    private val lastSendTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend operator fun invoke(contactId: String, fileUri: Uri) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastSend = lastSendTimes[contactId] ?: 0L
        if (now - lastSend < 500) {
            return@withContext // Debounce rapid double-taps
        }
        lastSendTimes[contactId] = now

        val contentResolver = context.contentResolver
        
        // 1. Get file metadata
        var fileName = "unknown_file"
        var fileSize = 0L
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        
        // 2. Generate random AES-256 key and 96-bit nonce (44 bytes total)
        val secureRandom = SecureRandom()
        val aesKey = ByteArray(32)
        val baseNonce = ByteArray(12)
        secureRandom.nextBytes(aesKey)
        secureRandom.nextBytes(baseNonce)
        
        // Generate Thumbnail if Image or Video
        var thumbnailUri: String? = null
        if (mimeType.startsWith("image/")) {
            thumbnailUri = fileUri.toString()
        } else if (mimeType.startsWith("video/")) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, fileUri)
                val bitmap = retriever.getFrameAtTime(1000000)
                retriever.release()
                
                if (bitmap != null) {
                    val thumbnailsDir = File(context.filesDir, "thumbnails")
                    if (!thumbnailsDir.exists()) thumbnailsDir.mkdirs()
                    val thumbFile = File(thumbnailsDir, "thumb_${UUID.randomUUID()}.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    thumbnailUri = thumbFile.absolutePath
                }
            } catch (e: Exception) {
                android.util.Log.e("SendFileUseCase", "Failed to extract video thumbnail", e)
            }
        }
        
        // 3. Save only the tiny 44-byte key material (Zero temp data file on disk!)
        val keysDir = File(context.filesDir, "transfer_keys")
        if (!keysDir.exists()) keysDir.mkdirs()
        
        val fileTransferId = UUID.randomUUID().toString()
        val keyFile = File(keysDir, fileTransferId)
        FileOutputStream(keyFile).use { out ->
            out.write(aesKey)
            out.write(baseNonce)
        }

        // 4. Create Message and FileTransfer in DB pointing directly to original URI
        val messageId = UUID.randomUUID().toString()
        val message = Message(
            id = messageId,
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            type = MessageType.FILE,
            content = "File: $fileName".toByteArray(Charsets.UTF_8),
            status = MessageStatus.PENDING,
            timestamp = now
        )
        
        val fileTransfer = FileTransfer(
            id = fileTransferId,
            messageId = messageId,
            fileName = fileName,
            fileSizeBytes = fileSize,
            mimeType = mimeType,
            localUri = fileUri.toString(),
            thumbnailUri = thumbnailUri
        )
        
        messageRepository.insertMessage(message)
        fileTransferRepository.insertFileTransfer(fileTransfer)
    }
}
