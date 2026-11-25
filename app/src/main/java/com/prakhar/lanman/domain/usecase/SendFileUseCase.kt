package com.prakhar.lanman.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.prakhar.lanman.data.security.SecureKeyStore
import com.prakhar.lanman.domain.model.FileTransfer
import com.prakhar.lanman.domain.model.Message
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.domain.repository.FileTransferRepository
import com.prakhar.lanman.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

class SendFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val fileTransferRepository: FileTransferRepository,
    private val secureKeyStore: SecureKeyStore
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

        // 1. Resolve file metadata
        var fileName = "unknown_file"
        var fileSize = 0L
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }
        if (fileSize <= 0L) {
            fileSize = runCatching {
                contentResolver.openAssetFileDescriptor(fileUri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0L } ?: 0L
                } ?: 0L
            }.getOrDefault(0L)
        }
        if (fileSize > MAX_SEND_FILE_BYTES) {
            throw IllegalArgumentException("File exceeds the 2 GB transfer limit")
        }

        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        val fileTransferId = UUID.randomUUID().toString()

        // 2. Ensure durable read access across app restarts / queued offline sends.
        // Photo Picker transient URIs cannot take persistable permissions, so stage those locally.
        val hasPersistableGrant = runCatching {
            contentResolver.takePersistableUriPermission(
                fileUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)

        val durableUri: String = if (hasPersistableGrant && fileSize > 0L) {
            fileUri.toString()
        } else {
            val outgoingDir = File(context.filesDir, "outgoing_files").apply { mkdirs() }
            val stagedFile = File(outgoingDir, fileTransferId)
            try {
                val input = contentResolver.openInputStream(fileUri)
                    ?: throw IOException("Cannot read selected file")
                input.use { src ->
                    FileOutputStream(stagedFile).use { dst ->
                        val buf = ByteArray(COPY_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = src.read(buf)
                            if (read == -1) break
                            copied += read
                            if (copied > MAX_SEND_FILE_BYTES) {
                                throw IllegalArgumentException("File exceeds the 2 GB transfer limit")
                            }
                            dst.write(buf, 0, read)
                        }
                    }
                }
                fileSize = stagedFile.length()
                stagedFile.absolutePath
            } catch (e: Exception) {
                stagedFile.delete()
                throw e
            }
        }

        // 3. Generate random AES-256 key and 96-bit nonce (44 bytes total)
        val secureRandom = SecureRandom()
        val aesKey = ByteArray(32)
        val baseNonce = ByteArray(12)
        secureRandom.nextBytes(aesKey)
        secureRandom.nextBytes(baseNonce)

        // 4. Generate Thumbnail if Image or Video
        var thumbnailUri: String? = null
        if (mimeType.startsWith("image/")) {
            thumbnailUri = durableUri
        } else if (mimeType.startsWith("video/")) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                if (durableUri.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(durableUri))
                } else {
                    retriever.setDataSource(durableUri)
                }
                val bitmap = retriever.getFrameAtTime(1_000_000L)
                if (bitmap != null) {
                    try {
                        val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                        val thumbFile = File(thumbnailsDir, "thumb_${UUID.randomUUID()}.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        thumbnailUri = thumbFile.absolutePath
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SendFileUseCase", "Failed to extract video thumbnail", e)
            } finally {
                runCatching { retriever.release() }
            }
        }

        // 5. Persist key material encrypted in Keystore-backed SecureKeyStore
        secureKeyStore.saveTransferKey(fileTransferId, aesKey + baseNonce)

        // 6. Create Message and FileTransfer in DB
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
            localUri = durableUri,
            thumbnailUri = thumbnailUri
        )

        messageRepository.insertMessage(message)
        fileTransferRepository.insertFileTransfer(fileTransfer)
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 256 * 1024
        const val MAX_SEND_FILE_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB
    }
}
